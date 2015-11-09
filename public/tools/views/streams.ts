import { Element, Property, Listener, Dependencies, Inject, On, PolymerElement, PolymerModelEvent } from "elements/polymer";
import { View, Tab, TabsGenerator } from "elements/app";
import { GtButton, GtForm, GtInput } from "elements/widgets";
import { GtBox, GtAlert } from "elements/box";
import { Streams, ActiveStream } from "services/streams";
import { throttled } from "utils/async";
import "services/roster";

const StreamsTabs: TabsGenerator = (view, path, user) => [
	{ title: "Live Streams", link: "/streams", active: view == "views/streams/GtStreams" },
	{ title: "Settings", link: "/streams/settings", active: view == "views/streams/GtStreamsSettings" },
	{ title: "Whitelist", link: "/streams/whitelist", active: view == "views/streams/GtStreamsWhitelist", hidden: !user.promoted }
];

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-player>

@Element("gt-streams-player", "/assets/views/streams.html")
@Dependencies(GtAlert)
export class GtStreamsPlayer extends PolymerElement {
	@Inject private service: Streams;
	
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
				let ticket = await this.service.requestTicket(this.stream.user);
				await this.removePlayer();
				let player = document.createElement("iframe");
				player.src = `/clappr/${ticket}`;
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
// <gt-streams>

@View("streams", StreamsTabs)
@Element("gt-streams", "/assets/views/streams.html")
@Dependencies(GtBox, GtStreamsItem, GtAlert, GtStreamsPlayer)
export class GtStreams extends PolymerElement {
	@Inject
	@On({ "offline": "StreamOffline" })
	private service: Streams;
	
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
@Dependencies()
export class GtStreamsSettings extends PolymerElement {
	@Inject private service: Streams;
}

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-whitelist>

@View("streams", StreamsTabs)
@Element("gt-streams-whitelist", "/assets/views/streams.html")
@Dependencies()
export class GtStreamsWhitelist extends PolymerElement {
	@Inject private service: Streams;
}

