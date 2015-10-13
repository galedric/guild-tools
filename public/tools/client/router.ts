import { Component, Injector } from "utils/di";
import { EventEmitter } from "utils/eventemitter";
import { Notify } from "utils/service";
import { PolymerConstructor, PolymerElement, Element, Inject, Bind, Property } from "elements/polymer";
import { GtApp } from "elements/app";
import { UserInformations } from "client/server";

// One specific route configuration
interface RoutePattern {
	pattern: RegExp;
	tags: string[];
	module: string;
	view: PolymerConstructor<any>;
}

// The object used to store route arguments
interface ArgumentsObject {
	[arg: string]: string;
}

// A module tab used in the titlebar
export interface ModuleTab {
	title: string;
	link: string;
	pattern?: RegExp;
	visible?: (user: UserInformations) => boolean;
}

/**
 * Routing component for GuildTools
 */
@Component
export class Router extends EventEmitter {
	/**
	 * This is the object that will receive @Route registration, this
	 * field is automatically set when calling loadRoutes().
	 */
	private static context: Router = null;

	/**
	 * Return the currently used router object
	 */
	public static getCurrent() {
		return Router.context;
	}

	/**
	 * Extract parameters names from view path and construct
	 * a equivalent regular expression for matching the path
	 */
	public static compilePath(path: string): [RegExp, string[]]{
		// Trim and escape base path
		// ... this may actually be a bad idea ...
		//path = path.replace(/\/$/, "").replace(/[.?*+^$[\]\\(){}|-]/g, "\\$&");
		// ... instead, only make parens non-capturing to prevent breaking tags capture ...
		path = path.replace(/\/$/, "").replace(/\(/g, "(?:");

		// Extract tag names
		let tags = (path.match(/[^?]:[a-z_0-9\-]+/g) || []).map(tag => tag.slice(2));

		// Replace tags with capturing placeholders
		path = path.replace(/([^?]):[a-z_0-9\-]+/g, "$1([^/]+)");

		return [new RegExp(`^${path}/?$`), tags];
	}

	// Routes definitions
	private routes: RoutePattern[] = [];
	private tabs: { [module: string]: ModuleTab[] } = {};

	// Current main-view informations
	@Notify public activeModule: string;
	@Notify public activeTabs: ModuleTab[];
	@Notify public activeView: PolymerConstructor<any>;
	@Notify public activeArguments: ArgumentsObject;
	@Notify public activePath: string;

	// User will be redirected to this path if no view matches the current path
	public fallback: string;

	// Keep track of last path to prevent view to be reloaded if the path hasn't actually changed
	private last_path: string;

	/**
	 * Bind to window popstate event
	 */
	constructor() {
		super();
		window.addEventListener("popstate", () => this.update());
	}

	/**
	 * Register a new route
	 */
	public register<T extends PolymerElement>(path: string, module: string, view: PolymerConstructor<T>) {
		let [pattern, tags] = Router.compilePath(path);
		this.routes.push({ pattern, tags, module, view });
	}

	/**
	 * Load all views from an AMD module
	 */
	public loadViews(module: string | string[]) {
		Router.context = this;
		return System.import(module).then(m => void 0);
	}

	/**
	 * Declare tabs for a specific module
	 */
	public static declareTabs(module: string, tabs: ModuleTab[]) {
		if (Router.context) {
			Router.context.tabs[module] = tabs;
		} else {
			throw new Error("Cannot declare tabs outside of a loadView() call");
		}
	}

	/**
	 * Update router state with current path
	 */
	public update() {
		// Current path
		let path = location.pathname;
		if (path == this.last_path) return;

		// Trim trailing slash if any
		if (path.match(/^\/.*\/$/)) {
			return this.goto(path.slice(0, -1));
		}

		// Search matching view
		for (let route of this.routes) {
			let matches = path.match(route.pattern);
			if (matches) {
				// Module and view constructor
				this.activeModule = route.module;
				this.activeTabs = this.tabs[route.module] || [];
				this.activeView = route.view;

				// Construct argument object
				let args = this.activeArguments = <ArgumentsObject> {};
				for (let i = 0; i < route.tags.length; ++i) {
					args[route.tags[i]] = matches[i + 1];
				}

				// Success
				this.last_path = path;
				this.activePath = path;
				return;
			}
		}

		// Nothing found, go to fallback
		if (this.fallback && path != this.fallback) {
			return this.goto(this.fallback, true);
		} else {
			this.activeModule = null;
			this.activeTabs = [];
			this.activeView = null;
			this.activeArguments = null;
			this.activePath = null;
		}
	}

	public goto(path: string, replace: boolean = false) {
		if (replace) {
			history.replaceState(null, "", path);
		} else {
			history.pushState(null, "", path);
		}
		this.update();
	}
}

/**
 * @View annotation, register the element as a view for a specific path
 * Automatically add @Element to the class
 */
export function View(module: string, selector: string, path: string) {
	return <T extends PolymerElement>(target: PolymerConstructor<T>) => {
		const element: PolymerConstructor<T> = Element(selector, `/assets/views/${module}.html`)(target);
		Router.getCurrent().register(path, module, element);
		return element;
	}
}
