import { Element, Dependencies, PolymerElement, Inject, Property, Listener, PolymerModelEvent } from "elements/polymer";
import { View, TabsGenerator } from "elements/app";
import { GtBox, GtAlert } from "elements/box";
import { GtButton } from "elements/widgets";
import { GtDialog } from "elements/dialog";
import { BnetThumb } from "elements/bnet";
import { GtTimeago } from "elements/timeago";
import { Server } from "client/server";
import { User, Roster } from "services/roster";
import { ApplyService, Apply } from "services/apply";
import { join } from "utils/async";

const ApplyTabs: TabsGenerator = (view, path, user) => [
	{ title: "Applications", link: "/apply", active: view == "views/apply/GtApply" },
	{ title: "Archives", link: "/apply/archives", active: view == "views/apply/GtApplyArchives" }
];

@Element("gt-apply-list-item", "/assets/views/apply.html")
@Dependencies(GtBox, BnetThumb, GtTimeago)
export class GtApplyListItem extends PolymerElement {
	@Property public apply: number;
}

@Element("gt-apply-details", "/assets/views/apply.html")
@Dependencies(GtBox, BnetThumb, GtTimeago)
export class GtApplyDetails extends PolymerElement {
	@Property public apply: number;
}

@View("apply", ApplyTabs)
@Element("gt-apply", "/assets/views/apply.html")
@Dependencies(GtBox, GtAlert, GtButton, GtDialog, GtApplyListItem, GtApplyDetails, Roster)
export class GtApply extends PolymerElement {
	@Inject
	private service: ApplyService;
	
	private applys: number[];
	
	@Property
	private selected: number = null;
	
	private async init() {
		this.applys = await this.service.openApplysList();
	}
	
	private SelectApply(ev: PolymerModelEvent<number>) {
		this.selected = ev.model.item;
	}
}

@View("apply", () => [{ title: "Apply", link: "/apply", active: true }])
@Element("gt-apply-redirect", "/assets/views/apply.html")
@Dependencies(GtButton)
export class GtApplyRedirect extends PolymerElement {
	@Listener("apply.click")
	private ApplyNow() { document.location.href = "http://www.fs-guild.net/tools/#/apply"; }
}
