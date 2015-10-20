import { Component } from "utils/di";
import { EventEmitter } from "utils/eventemitter";
import { Notify } from "utils/service";
import { Loader } from "client/loader";

// One specific route configuration
interface RoutePattern {
	pattern: RegExp;
	tags: string[];
	view: string;
}

// The object used to store route arguments
interface ArgumentsObject {
	[arg: string]: string;
}

// Extract parameters names from view path and construct
// an equivalent regular expression for matching the path
function compilePattern(path: string): [RegExp, string[]] {
	// Remove trailing slashes and make parens non-capturing
	// to prevent breaking tags capture
	path = path.replace(/\/$/, "").replace(/\(/g, "(?:");

	// Extract tag names
	let tags = (path.match(/[^?]:[a-z_0-9\-]+/g) || []).map(tag => tag.slice(2));

	// Replace tags with capturing placeholders
	path = path.replace(/([^?]):[a-z_0-9\-]+/g, "$1([^/]+)");

	return [new RegExp(`^${path}/?$`), tags];
}

/**
 * Routing component for GuildTools
 */
@Component
export class Router extends EventEmitter {
	// Routes definitions
	private routes: RoutePattern[] = [];

	// Current main-view informations
	@Notify public activePath: string;
	@Notify public activeArguments: ArgumentsObject;
	@Notify public activeView: string;

	// User will be redirected to this path if no view matches the current path
	public fallback: string;

	constructor(private loader: Loader) {
		super();
		window.addEventListener("popstate", () => this.update());
	}

	public async loadRoutes(path: string) {
		let routes = await this.loader.fetch(path);
		let entries = routes.match(/^\s*\S+\s+\S+\s*$/gm).map(l => l.match(/\S+/g));
		for (let entry of entries) {
			let [pattern, tags] = compilePattern(entry[0]);
			let view = entry[1];
			this.routes.push({ pattern, tags, view });
		};
	}

	public update() {
		// Current path
		let path = location.pathname;
		if (path == this.activePath) return;

		// Trim trailing slash if any
		if (path.match(/^\/.*\/$/)) {
			return this.goto(path.slice(0, -1));
		}

		// Search matching view
		for (let route of this.routes) {
			let matches = path.match(route.pattern);
			if (matches) {
				// Construct argument object
				let args = <ArgumentsObject> {};
				for (let i = 0; i < route.tags.length; ++i) {
					args[route.tags[i]] = matches[i + 1];
				}

				// Success
				this.activePath = path;
				this.activeArguments = args;
				this.activeView = route.view;
				return;
			}
		}

		// Nothing found, go to fallback
		if (this.fallback && path != this.fallback) {
			return this.goto(this.fallback, true);
		} else {
			this.activePath = null;
			this.activeArguments = null;
			this.activeView = null;
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
