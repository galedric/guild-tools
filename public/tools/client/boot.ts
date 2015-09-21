import { Deferred } from "utils/deferred";
import { Injector, Constructor } from "utils/di";
import { Application } from "client/main";

/**
 * Fix the mess made by the HTMLImports shim
 */
function fix_imports_shim() {
	const parser = (<any>window).HTMLImports.parser;
	if (parser) {
		const add_element = parser.addElementToDocument;
		parser.addElementToDocument = function(elt: Element) {
			add_element.apply(parser, arguments);
			if (elt.nodeName == "STYLE") {
				elt.addEventListener("load", () => {
					const parent = elt.parentNode;
					if (parent) parent.removeChild(elt);
				});
			}
		}
	}	
}

/**
 * Load and initialize Guild Tools
 * Create the default injector and use it
 * to construct the main Application object
 */
export default async function boot() {
	// Fix the html imports shim
	fix_imports_shim();
	
	// Load the default injector and the Application constructor
	const injector = await Deferred.require<Injector>("utils/di", "DefaultInjector");
	const app_constructor = await Deferred.require<Constructor<Application>>("client/main", "Application");
	
	// Construct the Application
	const app = injector.get(app_constructor);
	
	// Assign to global object and call main()
	(<any> window).GuildTools = app;
	try {
		await app.main();
	} catch (e) {
		console.error("loading failed", e);
	}
}
