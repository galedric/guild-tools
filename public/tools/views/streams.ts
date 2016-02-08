import { Element, Property, Listener, Dependencies, Inject, On, PolymerElement, PolymerModelEvent } from "elements/polymer";
import { View, Tab, TabsGenerator } from "elements/app";
import { GtButton, GtForm, GtInput, GtCheckbox, GtLabel } from "elements/widgets";
import { GtBox, GtAlert } from "elements/box";
import { StreamsService, ActiveStream } from "services/streams";
import { throttled, join } from "utils/async";
import "services/roster";

const StreamsTabs: TabsGenerator = (view, path, user) => [
	{ title: "Live Streams", link: "/streams", active: view == "views/streams/GtStreams" },
	{ title: "Publish", link: "/streams/settings", active: view == "views/streams/GtStreamsSettings" },
	{ title: "Whitelist", link: "/streams/whitelist", active: view == "views/streams/GtStreamsWhitelist", hidden: !user.promoted }
];

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-player>

@Element("gt-streams-player", "/assets/views/streams.html")
@Dependencies(GtAlert)
export class GtStreamsPlayer extends PolymerElement {
	@Inject private service: StreamsService;

	@Property({ observer: "update" })
	public stream: ActiveStream;

	private player: HTMLIFrameElement = null;
	private error: String = null;

	private async removePlayer() {
		while (this.player) {
			this.player.remove();
			this.player = null;
			await Promise.delay(400);
		}
	}

	private async update() {
		this.error = null;
		await this.removePlayer();

		if (this.stream) {
			try {
				let [ticket, stream] = await this.service.requestTicket(this.stream.user);
				await this.removePlayer();
				let player = document.createElement("iframe");
				player.src = `/clappr/${stream}?${ticket}`;
				player.allowFullscreen = true;
				this.player = player;
				this.shadow.appendChild(player);
			} catch (e) {
				this.error = e.message;
			}
		}
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-item>

@Element("gt-streams-item", "/assets/views/streams.html")
@Dependencies(GtBox)
export class GtStreamsItem extends PolymerElement {
	@Property public stream: ActiveStream;
}

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-viewers-item>

@Element("gt-streams-viewers-item", "/assets/views/streams.html")
@Dependencies(GtBox)
export class GtStreamsViewersItem extends PolymerElement {
	@Property public user: number;
}

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-viewers>

@Element("gt-streams-viewers", "/assets/views/streams.html")
@Dependencies(GtStreamsViewersItem, GtBox)
export class GtStreamsViewers extends PolymerElement {
	@Inject
	@On({
		"list-update": "Update",
		"notify": "Update"
	})
	private service: StreamsService;

	// The currently selected stream
	@Property({ observer: "Update" })
	public stream: ActiveStream;

	// List of viewers for this stream
	protected viewers: number[];

	// Update the viewers list
	protected Update() {
		if (!this.stream) return;
		this.viewers = this.service.getStreamViewers(this.stream.user);
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-streams>

@View("streams", StreamsTabs)
@Element("gt-streams", "/assets/views/streams.html")
@Dependencies(GtBox, GtStreamsItem, GtStreamsViewers, GtAlert, GtStreamsPlayer)
export class GtStreams extends PolymerElement {
	@Inject
	@On({ "offline": "StreamOffline" })
	private service: StreamsService;

	@Property public selected: ActiveStream = null;

	private StreamOffline(user: number) {
		if (this.selected && this.selected.user == user) {
			this.selected = null;
		}
	}

	private SelectStream(ev: PolymerModelEvent<ActiveStream>) {
		let stream = ev.model.item;
		this.selected = (this.selected && this.selected.user == stream.user) ? null : stream;
	}

	private StreamIsActive(user: number) {
		return this.selected && this.selected.user == user;
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-settings>

@View("streams", StreamsTabs)
@Element("gt-streams-settings", "/assets/views/streams.html")
@Dependencies(GtBox, GtButton, GtCheckbox, GtLabel)
export class GtStreamsSettings extends PolymerElement {
	@Inject private service: StreamsService;

	public token: string;
	public key: string;
	public visibility: string;

	private visibility_check: boolean = false;

	@join private async updateToken() {
		let data = await this.service.ownTokenVisibility();
		if (data) {
			let [token, key, progress] = data;
			this.token = token;
			this.key = key;
			this.visibility = progress ? "limited" : "open";
		} else {
			this.token = null;
			this.key = null;
			this.visibility = "open";
		}
	}

	@Property({ computed: "token" })
	private get hasToken(): boolean {
		return this.token !== null;
	}

	private init() {
		this.updateToken();
	}

	private generating = false;
	private async Generate() {
		if (this.generating) return;
		this.generating = true;
		try {
			await this.service.createToken();
			this.updateToken();
		} finally {
			this.generating = false;
		}
	}

	private async UpdateVisibility() {
		this.visibility_check = false;
		await this.service.changeOwnVisibility(this.visibility == "limited");
		this.visibility_check = true;
		this.debounce("hide_visibility_check", () => this.visibility_check = false, 3000);
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-whitelist>

@View("streams", StreamsTabs)
@Element("gt-streams-whitelist", "/assets/views/streams.html")
@Dependencies()
export class GtStreamsWhitelist extends PolymerElement {
	@Inject private service: StreamsService;
}

