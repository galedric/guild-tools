import {PolymerElement} from "../../polymer/PolymerElement";
import {Property, Element} from "../../polymer/Annotations";
import { parse, Renderer } from "marked";

const options: MarkedOptions = <any> {
	gfm: true,
	tables: true,
	breaks: true,
	sanitize: true,
	smartypants: true
};

const renderer = options.renderer = new Renderer(options);

renderer.heading = (text, level) => (level > 2) ? `<p>${text}</p>` : `<h${level}>${text}</h${level}>`;
renderer.link = (href, title, text) => Renderer.prototype.link.call(renderer, href, title, text).replace(/^<a/, `<a target="_blank"`);

@Element({
	selector: "gt-markdown",
	template: "/assets/imports/markdown.html"
})
export class GtMarkdown extends PolymerElement {
	@Property({ observer: "update" })
	public src: string;

	public async update() {
		Polymer.dom(this.$.markdown).innerHTML = parse(this.src, options);
		await microtask;
		this.fire("rendered");
	}
}