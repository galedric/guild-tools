import {Element, Inject, On, Property, Listener} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";
import {GtDialog} from "../widgets/GtDialog";
import {GtTextarea} from "../widgets/GtTextarea";
import {GtLabel} from "../widgets/GtLabel";
import {GtCheckbox} from "../widgets/GtCheckbox";
import {GtTimeago} from "../misc/GtTimeago";
import {GtBox} from "../widgets/GtBox";
import {ApplyService, Apply, ApplyMessage} from "../../services/apply/ApplyService";
import {defer, throttled} from "../../utils/Async";
import {GtMarkdown} from "../misc/GtMarkdown";
import {ApplyManage} from "./ApplyManage";
import {Char} from "../../services/roster/RosterService";
import {ApplyDataProvider} from "../../services/apply/ApplyProviders";
import {RosterMainProvider} from "../../services/roster/RosterProviders";


@Element({
	selector: "apply-details-char",
	template: "/assets/views/apply.html",
	dependencies: [GtBox, GtTimeago, ApplyDataProvider, RosterMainProvider]
})
export class ApplyDetailsChar extends PolymerElement {
	@Property public id: number;

	private char: Char;

	@Property({ computed: "char.server" })
	private get serverName(): string {
		return this.char.server.replace(/-/g, " ").replace(/\b([a-z])([a-z]+)/g, (all, first, tail) => {
			return (tail.length > 2) ? first.toUpperCase() + tail : first + tail;
		});
	}

	@Listener("click")
	private OnClick() {
		window.open(`http://eu.battle.net/wow/en/character/${this.char.server}/${this.char.name}/advanced`);
	}
}

@Element({
	selector: "apply-details-message",
	template: "/assets/views/apply.html",
	dependencies: [GtBox, GtTimeago, GtMarkdown]
})
export class ApplyDetailsMessage extends PolymerElement {
	@Property public message: ApplyMessage;
}

@Element({
	selector: "apply-details",
	template: "/assets/views/apply.html",
	dependencies: [GtBox, GtTimeago, ApplyDetailsChar, ApplyDetailsMessage, GtCheckbox,
		GtLabel, GtTextarea, GtDialog, ApplyManage]
})
export class ApplyDetails extends PolymerElement {
	@Inject
	@On({
		"message-posted": "MessagePosted",
		"unread-updated": "UnreadUpdated"
	})
	private service: ApplyService;

	/**
	 * The application ID
	 */
	@Property({observer: "ApplyChanged"})
	public apply: number;

	/**
	 * Apply meta-informations, loaded by a <meta> tag
	 */
	private data: Apply;

	/**
	 * Track which tab is currently activated
	 */
	private tab: number;

	/**
	 *  The edit form is open
	 */
	private editOpen: boolean;

	/**
	 * Edit form disabled during message sending
	 */
	private editorDisabled: boolean;

	/**
	 * The message body
	 */
	private messageBody: string;

	/**
	 * Message type, can be "private" or "public"
	 */
	private messageType: string;

	/**
	 * Generate a fake ApplyMessage for the preview function
	 * It will be continuously updated with the content of the
	 * edit box.
	 */
	@Property({computed: "messageBody messageType"})
	private get messagePreview(): ApplyMessage {
		defer(() => this.ScrollDown());
		return {
			id: 0,
			apply: this.apply,
			user: this.app.user.id,
			date: new Date(),
			text: this.messageBody,
			secret: this.messageType == "private",
			system: false
		};
	};

	/**
	 * The discussion feed data
	 */
	private feed: ApplyMessage[];

	/**
	 * The apply form data
	 */
	private body_type: number;
	private body: any;

	/**
	 * Timeout for sending the apply-seen message
	 * When application is changed before 2s, the set-seen message
	 * is not sent
	 */
	private seenTimeout: any;

	/**
	 * Called when the selected apply changes
	 * Reset every state that may have changed
	 */
	private async ApplyChanged() {
		this.tab = void 0;
		this.editOpen = false;
		this.editorDisabled = false;
		this.messageBody = "";
		this.messageType = this.app.user.member ? "private" : "public";
		this.feed = [];
		clearTimeout(this.seenTimeout);

		if (!this.apply) return;

		this.tab = 1;
		let [feed, [body_type, body]] = await Promise.atLeast(200, this.service.applyFeedBody(this.apply));

		this.feed = feed;
		this.body_type = body_type;
		switch (body_type) {
			case 0:
				this.body = null;
				break;
			case 1:
				this.body = JSON.parse(body);
				break;
		}

		if (this.service.unreadState(this.apply)) {
			this.seenTimeout = setTimeout(() => this.service.setSeen(this.apply), 2000);
		}
	}

	/**
	 * Tab handlers
	 */
	private ShowDiscussion() {
		this.tab = 1;
		this.editOpen = false;
	}

	private ShowDetails() {
		this.tab = 2;
		this.editOpen = false;
	}

	private ShowManage() {
		this.$.manage.show();
	}

	@Listener("manage-dialog.close-manage-dialog")
	private OnCloseManageDialog() {
		this.$.manage.hide();
	}

	/**
	 * Scroll the discussion tab to the bottom
	 * Attempt to be as smart as possible to prevent automatic scrolling
	 * if user scrolled manually
	 */
	@throttled
	private async ScrollDown(force?: boolean) {
		let node = this.$.discussion.$.wrapper;
		let bottom = node.scrollTop + node.clientHeight + 200;
		if (force || node.scrollTop < 50 || bottom > node.scrollHeight) {
			node.scrollTop = node.scrollHeight;
		}
	}

	/**
	 * Catch message rendered event and trigger automatic scrolldown
	 * Also bind to messages images
	 */
	@Listener("discussion.rendered")
	private async MessageRendered(ev: any) {
		// Get every images in the message
		let markdown = Polymer.dom(ev).rootTarget.$.markdown;
		let imgs = markdown.querySelectorAll("img");

		// Scroll when they are loaded
		for (let i = 0; i < imgs.length; i++) {
			let img: HTMLImageElement = imgs[i];
			Promise.onload(img).finally(() => this.ScrollDown());
		}

		// Scroll anyway at the end of the microtask
		await microtask;
		this.ScrollDown();
	}

	/**
	 * Open the new message editor
	 */
	private async OpenEdit() {
		this.editOpen = true;
		await Promise.delay(200);
		this.ScrollDown(true);
	}

	/**
	 * Send message to server
	 */
	private async SendMessage() {
		this.editorDisabled = true;
		let success = await Promise.atLeast(500, this.service.postMessage(this.apply, this.messageBody, this.messageType != "public"));
		if (success) {
			this.messageBody = "";
			this.messageType = "private";
			this.editOpen = false;
		}
		this.editorDisabled = false;
	}

	/**
	 * Close the new message editor
	 */
	private CloseEdit() {
		this.editOpen = false;
	}

	/**
	 * New message posted on a application feed
	 */
	private MessagePosted(message: ApplyMessage) {
		if (message.apply == this.apply) {
			this.push("feed", message);
		}
	}

	/**
	 * Catch update to unread state for an open application
	 * and cancel it
	 */
	private UnreadUpdated(apply: number, unread: boolean) {
		if (apply == this.apply && unread) {
			this.service.setSeen(apply);
		}
	}

	@Property({computed: "editorDisabled app.user.member"})
	private get privateMessageDisabled(): boolean {
		return this.editorDisabled || !this.app.user.member;
	}

	/**
	 * Generate the link to the user profile
	 */
	@Property({computed: "data.user"})
	private get profileLink(): string {
		return "/profile/" + this.data.user;
	}
}