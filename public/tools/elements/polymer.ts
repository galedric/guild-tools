import { Deferred, LazyThen, defer } from "utils/deferred";
import { Queue } from "utils/queue";
import { Constructor, DefaultInjector } from "utils/di";
import { EventEmitter } from "utils/eventemitter";
import { Service } from "utils/service";
import { Application } from "client/main";
import { Loader } from "client/loader";

/**
 * Dummy class to expose Polymer functions on elements
 */
export class PolymerElement {
	//protected root: DocumentFragment;
	protected node: ShadyDOM;
	protected shadow: ShadyDOM;
	
	/**
	 * Reference to the GT Application object
	 */
	protected app: Application;

	/**
	 * The Dollar Helper
	 */
	protected $: any;

	/**
	 * Returns the first node in this element's local DOM that matches selector
	 */
	protected $$: (selector: string) => Element;

	/**
	 * Toggles the named boolean class on the host element, adding the class if
	 * bool is truthy and removing it if bool is falsey.
	 * If node is specified, sets the class on node instead of the host element.
	 */
	protected toggleClass: (name: string, bool: boolean, node?: Element) => void;

	/**
	 * Like toggleClass, but toggles the named boolean attribute.
	 */
	protected toggleAttribute: (name: string, bool: boolean, node?: Element) => void;

	/**
	 * Moves a boolean attribute from oldNode to newNode, unsetting the attribute
	 * (if set) on oldNode and setting it on newNode.
	 */
	protected attributeFollows: (name: string, newNode: HTMLElement, oldNode: HTMLElement) => void;

	/**
	 * Removes a class from one node, and adds it to another.
	 */
	protected classFollows: (name: string, newNode: HTMLElement, oldNode: HTMLElement) => void;

	/**
	 * Force this element to distribute its children to its local dom.
	 * A user should call distributeContent if distribution has been invalidated due to
	 * changes to selectors on child elements that effect distribution that were not made
	 * via Polymer.dom.
	 * For example, if an element contains an insertion point with <content select=".foo">
	 * and a foo class is added to a child, then distributeContent must be called to update
	 * local dom distribution.
	 */
	protected distributeContent: () => void;

	protected getContentChildNodes: (selector?: string) => Node[];
	protected getContentChildren: (selector?: string) => Element[];

	/**
	 * Fires a custom event.
	 */
	protected fire: PolymerFireInterface;

	/**
	 * Calls method asynchronously. If no wait time is specified, runs tasks with microtask timing
	 * (after the current method finishes, but before the next event from the event queue is processed).
	 * Returns a handle that can be used to cancel the task.
	 */
	protected async: (method: string, wait?: number) => PolymerAsyncHandler;

	/**
	 * Cancels the identified async task
	 */
	protected cancelAsync: (handler: PolymerAsyncHandler) => void;

	/**
	 * Call debounce to collapse multiple requests for a named task into one invocation,
	 * which is made after the wait time has elapsed with no new request.
	 * If no wait time is given, the callback is called at microtask timing
	 * (guaranteed to be before paint).
	 */
	protected debounce: (jobName: string, callback: Function, wait?: number) => void;

	/**
	 * Cancels an active debouncer without calling the callback.
	 */
	protected cancelDebouncer: (jobName: string) => void;

	/**
	 * Calls the debounced callback immediately and cancels the debouncer.
	 */
	protected flushDebouncer: (jobName: string) => void;

	/**
	 * Returns true if the named debounce task is waiting to run.
	 */
	protected isDebouncerActive: (jobName: string) => boolean;

	/**
	 * Applies a CSS transform to the specified node, or this element
	 * if no node is specified. transform is specified as a string.
	 */
	protected transform: (transform: string, node?: Element) => void;

	/**
	 * Transforms the specified node, or this element if no node is specified.
	 */
	protected translate3d: (x: string, y: string, z: string, node?: Element) => void;

	/**
	 * Dynamically imports an HTML document
	 */
	protected importHref: (href: string, onload: Function, onerror: Function) => void;

	/**
	 * Return the element whose local dom within which this element is contained.
	 */
	protected host: <T extends PolymerElement>(ctor: PolymerConstructor<T>) => T;

	/**
	 * Removes an item from an array, if it exists.
	 */
	protected arrayDelete: <T>(path: string | Array<T>, item: any) => Array<T>;

	/**
	 * Adds items onto the end of the array at the path specified.
	 * This method notifies other paths to the same array that a splice occurred to the array.
	 */
	protected push: (path: string, ...values: any[]) => number;

	/**
	 * Removes an item from the end of array at the path specified.
	 * This method notifies other paths to the same array that a splice occurred to the array.
	 */
	protected pop: (path: string) => any;

	/**
	 * Removes an item from the beginning of array at the path specified.
	 * This method notifies other paths to the same array that a splice occurred to the array.
	 */
	protected shift: (path: string) => any;

	/**
	 * Adds items onto the beginning of the array at the path specified.
	 * This method notifies other paths to the same array that a splice occurred to the array.
	 */
	protected unshift: (path: string, ...values: any[]) => number;

	/**
	 * Starting from the start index specified, removes 0 or more items from the
	 * array and inserts 0 or more new itms in their place.
	 * This method notifies other paths to the same array that a splice occurred to the array.
	 */
	protected splice: (path: string, start: number, deleteCount: number, ...newElements: any[]) => number;

	/**
	 * Convienence method for setting a value to a path and notifying any elements bound to the same path.
	 */
	protected set: (path: any, value: any, root?: any) => void;

	/**
	 *
	 */
	//protected notifyPath: (path: string, value: )

	/**
	 * Convenience method to add an event listener on a given element,
	 * late bound to a named method on this element.
	 */
	protected listen: <T>(element: Node | PolymerElement, event: string, method: string) => void;

	/**
	 * Re-evaluates and applies custom CSS properties based on dynamic changes to this
	 * element's scope, such as adding or removing classes in this element's local DOM.
	 * For performance reasons, Polymer's custom CSS property shim relies on this explicit
	 * signal from the user to indicate when changes have been made that affect the values
	 * of custom properties.
	 */
	protected updateStyles: () => void;

	/**
	 * Prevent further event propagation
	 */
	protected stopEvent: (e: Event) => boolean;

	/**
	 * Add an event listener to this element
	 */
	public addEventListener: (event: string, handler: (e: Event) => void) => void;
}

/**
 * Interface of PolymerElement#fire
 */
interface PolymerFireInterface {
	(type: string, options?: PolymerFireOptions): void;
	(type: string, details: any, options?: PolymerFireOptions): void;
}

/**
 * PolymerElement#fire options object
 */
interface PolymerFireOptions {
	node?: HTMLElement;
	bubbles?: boolean;
	cancelable?: boolean;
}

/**
 * Dummy interface for the opaque type of the value returned by PolymerElement#async
 */
interface PolymerAsyncHandler {}

/**
 * Interface of a Polymer constructor function
 */
export interface PolymerConstructor<T extends PolymerElement> extends Function {
	new (): T;
}

/**
 * The Polymer returned to replace the original class constructor
 */
export interface PolymerProxy<T extends PolymerElement> extends PolymerConstructor<T> {
	new (): T;
	[key: string]: any;
}

/**
 * Metadata about the Polymer element
 */
export interface PolymerMetadata<T extends PolymerElement> {
	selector: string;
	template: string;
	proto: any;
	dependencies: PolymerConstructor<any>[];
	loaded: boolean;
	domModule?: HTMLElement;
	constructor?: Function;
	base?: Function;
}

/**
 * Polymer powered events
 */
export interface PolymerModelEvent<T> extends Event {
	model: { item: T };
}

/**
 * Registration of DI-enabled properties
 */
interface InjectionBinding {
	ctor: Constructor<any>;
	property: string;
}

interface EventMapping {
	[event: string]: string | boolean;
}

interface ElementBindings {
	[property: string]: EventMapping;
}

/**
 * Declare a Polymer Element
 */
export function Element(selector: string, template?: string, ext?: string) {
	return <T extends PolymerElement>(target: PolymerConstructor<T>) => {
		// Register the element selector
		target.prototype.is = selector;
		
		// Register the extension of native element
		if (ext) target.prototype.extends = ext;

		const initialize = (that: any, args?: any) => {
			if (!Reflect.getMetadata<boolean>("polymer:initialized", that)) {
				Reflect.defineMetadata("polymer:initialized", true, that);
				target.apply(that, args);
				if (that.init) {
					that.init.apply(that, args);
				}
			}
		}

		// Proxy to constructor function
		target.prototype.factoryImpl = function() {
			initialize(this, arguments);
		};

		// Define custom sugars and perform dependency injection
		target.prototype.createdCallback = function() {
			Polymer.Base.createdCallback.apply(this, arguments);

			// If the element wasn't created using the new operator, call the constructor
			defer(() => initialize(this));

			this.node = Polymer.dom(this);
			this.shadow = Polymer.dom(this.root);
			this.app = DefaultInjector.get<Application>(Application);

			const injects = Reflect.getMetadata<InjectionBinding[]>("polymer:injects", target.prototype);
			if (injects) {
				for (let binding of injects) {
					this[binding.property] = DefaultInjector.get(binding.ctor);
				}
			}
		};

		// When the element is attached, register every listener defined using
		// the @On annotation. Also call the constructor if not yet done.
		target.prototype.attachedCallback = function() {
			Polymer.Base.attachedCallback.apply(this, arguments);

			// If the element wasn't created using the new operator, call the constructor
			initialize(this);

			// Handler for binding property
			const create_bind_handler = (property: string, emitter: any) => {
				const parts = property.match(/^(.*)\|(.*)$/);
				this[parts[2]] = emitter[parts[1]];
				return function(value: any) { this[parts[2]] = value; };
			}

			// Attach events
			const bindings = Reflect.getMetadata<ElementBindings>("polymer:bindings", target.prototype);
			if (bindings) {
				for (let property in bindings) {
					// Get the EventEmitter object and ensure it is the correct type
					const emitter = <EventEmitter> this[property];
					if (!(emitter instanceof EventEmitter)) continue;

					const mapping = bindings[property];
					for (let event in mapping) {
						const handler: string = mapping[event] === true ? event : mapping[event];
						const fn = (handler.slice(0, 5) == "bind@") ? create_bind_handler(handler.slice(5), emitter) : this[handler];
						if (typeof fn == "function") {
							emitter.on(event, fn, this);
						}
					}
				}
			}

			// Attach to pausable emitters
			const injects = Reflect.getMetadata<InjectionBinding[]>("polymer:injects", target.prototype);
			if (injects) {
				for (let binding of injects) {
					const injected: Service = this[binding.property]
					if (injected instanceof Service) {
						injected.attachListener(this);
					}
				}
			}
		};

		target.prototype.detachedCallback = function() {
			Polymer.Base.detachedCallback.apply(this, arguments);

			// Detach from pausable emitters
			const injects = Reflect.getMetadata<InjectionBinding[]>("polymer:injects", target.prototype);
			if (injects) {
				for (let binding of injects) {
					const injected: Service = this[binding.property]
					if (injected instanceof Service) {
						injected.detachListener(this);
					}
				}
			}

			// Detach events
			const bindings = Reflect.getMetadata<ElementBindings>("polymer:bindings", target.prototype);
			if (bindings) {
				for (let property in bindings) {
					// Get the EventEmitter object and ensure it is the correct type
					const emitter = <EventEmitter> this[property];
					if (!(emitter instanceof EventEmitter)) continue;

					const mapping = bindings[property];
					for (let event in mapping) {
						const handler = mapping[event] === true ? event : mapping[event];
						const fn = this[handler];
						if (typeof fn == "function") {
							emitter.off(event, fn, this);
						}
					}
				}
			}
		};

		// Reference to the element metadata object
		let meta: PolymerMetadata<T>;

		// The placeholder during element loading
		const proxy: PolymerProxy<T> = <any> function() {
			if (!meta.constructor) throw new Error("Polymer element is not yet loaded");
			return meta.constructor.apply(Object.create(meta.constructor.prototype), arguments);
		};

		// Get metadata object or create it
		meta = Reflect.getMetadata("polymer:meta", target) || <any> {};
		meta.selector = selector;
		meta.template = template;
		meta.base = target;
		meta.proto = target.prototype;
		meta.loaded = false;

		// Copy it on the proxy
		Reflect.defineMetadata("polymer:meta", meta, proxy);

		// Transpose static members on the proxy function
		for (let key in target) {
			if (target.hasOwnProperty(key)) {
				proxy[key] = (<PolymerProxy<T>> target)[key];
			}
		}
		
		// There is no attached template, load the element as soon as polymer is loaded
		if (!template) {
			DefaultInjector.get<Loader>(Loader).registerPolymerAutoload(proxy);
		}

		return <any> proxy;
	};
}

/**
 * Delcare Polymer element dependencies
 */
export function Dependencies(...dependencies: PolymerConstructor<any>[]) {
	return <T extends PolymerElement>(target: PolymerConstructor<T>) => {
		const meta: PolymerMetadata<T> = Reflect.getMetadata("polymer:meta", target) || <any> {};
		meta.dependencies = dependencies;
		Reflect.defineMetadata("polymer:meta", meta, target);
	};
}

/**
 * Declare a Polymer Property
 */
interface PolymerPropertyConfig<T> {
	type?: { new (): T };
	value?: T;
	reflect?: boolean;
	reflectToAttribute?: boolean;
	readOnly?: boolean;
	notify?: boolean;
	computed?: string;
	observer?: string;
}

export function Property<T>(config: PolymerPropertyConfig<T>): (t: any, p: string) => void;
export function Property<T>(target: any, property: string): void;
export function Property<T>(target: any, property: string, config: PolymerPropertyConfig<T>): void;

export function Property<T>(target?: any, property?: string, config: PolymerPropertyConfig<T> = {}): any {
	// Called with a config object
	if (!(target instanceof PolymerElement)) {
		return (t: any, p: string) => Property(t, p, target);
	}
	
	if (!target.properties) target.properties = {};
	
	if (config.reflect) {
		config.reflectToAttribute = true;
	}

	if (config.computed) {
		try {
			const generator = Object.getOwnPropertyDescriptor(target, property).get;
			const updater_key = `_${property.replace(/\W/g, "_")}`;
			target[updater_key] = generator;
			delete target[property];
			config.computed = `${updater_key}(${config.computed.replace(/\s+/g, ",")})`;
		} catch (e) {
			console.error(`Failed to generate computed property '${property}'`);
			throw e;
		}
	}
	
	if (typeof config == "object" && !config.type) {
		config.type = Reflect.getMetadata<any>("design:type", target, property);
	}

	target.properties[property] = config;
}

/**
 * Declare a Polymer Property
 */
export function Listener(...events: string[]) {
	return (target: any, property: string) => {
		if (!target.listeners) target.listeners = {};
		for (let event of events) target.listeners[event] = property;
	};
}

/**
 * Declare a Polymer dependency injection
 */
export function Inject<T>(target: any, property: string) {
	// Get the field type constructor
	const ctor = Reflect.getMetadata<Constructor<any>>("design:type", target, property);

	// Get the list of injections for this element and insert a new one
	let injects = Reflect.getMetadata<InjectionBinding[]>("polymer:injects", target) || [];
	injects.push({ ctor, property });
	Reflect.defineMetadata("polymer:injects", injects, target);
}

/**
 * Normalize mapping
 */
type ExtendedMapping = EventMapping | string[] | string;
function normalize_mapping(mapping: ExtendedMapping): EventMapping {
	if (typeof mapping === "string") {
		return { [mapping]: true };
	} else if (Array.isArray(mapping)) {
		const norm: EventMapping = {};
		mapping.forEach(k => norm[k] = true);
		return norm;
	} else {
		return mapping;
	}    
}

/**
 * Declare event biding with externals modules
 */
export function On(m: ExtendedMapping) {
	const mapping = normalize_mapping(m);
	return (target: any, property: string) => {
		let bindings = Reflect.getMetadata<ElementBindings>("polymer:bindings", target) || {};
		bindings[property] = bindings[property] || {};
		for (let key in mapping) {
			bindings[property][key] = mapping[key];
		}
		Reflect.defineMetadata("polymer:bindings", bindings, target);
	};
}

/**
 * Same as @On but automatically adjust for @Notify naming convention
 */
export function Watch(m: ExtendedMapping) {
	const mapping = normalize_mapping(m);
	const ajusted_mapping: { [key: string]: any } = {}
	for (let key in mapping) {
		ajusted_mapping[`${key}-updated`] = mapping[key];
	}
	return On(ajusted_mapping);
}

/**
 * Same as @Watch but additionally automatically set own property
 */
export function Bind(m: ExtendedMapping) {
	const mapping = normalize_mapping(m);
	const ajusted_mapping: { [key: string]: any } = {}
	for (let key in mapping) {
		if (typeof mapping[key] == "boolean") mapping[key] = key;
		ajusted_mapping[`${key}-updated`] = `bind@${key}|${mapping[key]}`;
	}
	return On(ajusted_mapping);
}

/**
 * Apply additionnal functions on the Polymer object
 */
export function apply_polymer_fns() {
	/**
	 * Check if an Node is an instance of the given Polymer element
	 */
	Polymer.is = <T extends PolymerElement>(node: Node, ctor: PolymerConstructor<T>) => {
		const selector = Reflect.getMetadata<{ selector: string; }>("polymer:meta", ctor).selector;
		return (<any> node).is == selector;
		//return Object.getPrototypeOf(node) === Polymer.Base.getNativePrototype(selector);
	};

	/**
	 * Type-safe cast of Node to Polymer elements
	 */
	Polymer.cast = <T extends PolymerElement>(node: Node, ctor: PolymerConstructor<T>) => {
		if (Polymer.is(node, ctor)) {
			return node;
		} else {
			const selector = Reflect.getMetadata<{ selector: string; }>("polymer:meta", ctor).selector;
			throw new TypeError(`Node <${node.nodeName}> is not castable to <${selector}>`);
		}
	};

	/**
	 * Find the closed parent node of a given type
	 * TODO: prevent crossing shadow-dom boundaries
	 */
	Polymer.enclosing = <T extends PolymerElement>(node: Node, ctor: PolymerConstructor<T>) => {
		const initial_name = node.nodeName;
		do {
			node = node.parentNode;
		} while (node && !Polymer.is(node, ctor));
		//if (!node) throw new SyntaxError(`<${initial_name}> is not enclosed by a <${ctor.__polymer.selector}>`);
		return node;
	};

	const Base: any = Polymer.Base;

	/**
	 * Find the closed host element of a given type
	 */
	Base.host = function <T extends PolymerElement>(ctor: PolymerConstructor<T>): T {
		return Polymer.enclosing(this, ctor);
	};

	/**
	 * Prevent further event propagation
	 */
	Base.stopEvent = function(e: Event) {
		e.stopImmediatePropagation();
		e.preventDefault();
		return false;
	};
}
