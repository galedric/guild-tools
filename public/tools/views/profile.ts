import { Element, Property, Listener, Dependencies, Inject, On, PolymerElement } from "elements/polymer";
import { View, Tab, TabsGenerator } from "elements/app";
import { GtButton, GtForm, GtInput } from "elements/widgets";
import { BnetThumb } from "elements/bnet";
import { GtDialog } from "elements/dialog";
import { GtBox } from "elements/box";
import { GtTimeago } from "elements/timeago";
import { RosterService, User, Char } from "services/roster";
import { ProfileService, ProfileData } from "services/profile";
import { throttled } from "utils/async";
import moment from "moment";

const ProfileTabs: TabsGenerator = (view, path, user) => [
	{ title: "Profile", link: "/profile", active: view == "views/profile/GtProfile" }
];

///////////////////////////////////////////////////////////////////////////////
// <profile-user>

@Element("profile-user", "/assets/views/profile.html")
@Dependencies(GtBox, BnetThumb)
export class ProfileUser extends PolymerElement {
	@Property public user: number;
}

///////////////////////////////////////////////////////////////////////////////
// <profile-infos>

@Element("profile-infos", "/assets/views/profile.html")
@Dependencies(GtBox, GtButton)
class ProfileInfos extends PolymerElement {
	/**
	 * ProfileService instance
	 */
	@Inject
	private service: ProfileService;

	/**
	 * The user id
	 */
	@Property({ observer: "FetchProfile" })
	public user: number;

	/**
	 * User's profile data
	 */
	private data: ProfileData;

	/**
	 * Return the user's realname
	 */
	@Property({ computed: "data" })
	public get realname() {
		return this.data.realname || "—";
	}

	/**
	 * Return the user's BattleTag
	 */
	@Property({ computed: "data" })
	public get btag() {
		return this.data.btag || "—";
	}

	/**
	 * Return the user's phone number
	 * @returns {string}
	 */
	@Property({ computed: "data" })
	public get phone() {
		if (!this.data.phone) return "—";
		let phone = this.data.phone;

		// Attempt to format the phone number
		[
			"+33 x xx xx xx xx",
			"+41 xx xxx xx xx",
			"+32 xxx xx xx xx",
			"+222 xxxx xxxx"
		].forEach(format => {
			let prefix_len = format.indexOf(" ");
			let prefix = format.slice(0, prefix_len);

			if (prefix == phone.slice(0, prefix_len)) {
				let digits = phone.slice(prefix_len).split("");
				let formatted = prefix;

				for (let i = prefix_len; i < format.length; i++) {
					let char = format[i];
					if (char == "x" && digits.length != 0) {
						char = digits.shift();
					}
					formatted += char;
				}

				if (digits.length != 0) {
					formatted += " " + digits.join("")
				}

				phone = formatted;
			}
		});

		return phone;
	}

	/**
	 * Return the user's age
	 */
	@Property({ computed: "data" })
	public get age() {
		if (!this.data.birthday) return "—";
		let now = moment();
		let birth = moment(this.data.birthday);
		return now.diff(birth, "years").toString();
	}

	/**
	 * Return the user's mail
	 */
	@Property({ computed: "data" })
	public get mail() {
		return this.data.mail || "—";
	}

	/**
	 * Return the user's real-life location
	 * @returns {string}
	 */
	@Property({ computed: "data" })
	public get location() {
		return this.data.location || "—";
	}

	/**
	 * Fetch the user's profile.
	 * Called automatically when the user is is available.
	 */
	private async FetchProfile() {
		if (this.fetching) return;
		this.fetching = true;
		this.data = await this.service.userProfile(this.user);
		this.fetching = false;
	}
	private fetching: boolean = false;

	/**
	 * Check if the user profile is editable by the current user
	 */
	@Property({ computed: "user" })
	private get editable(): boolean {
		return this.app.user.id == this.user || this.app.user.promoted;
	}
}

///////////////////////////////////////////////////////////////////////////////
// <profile-chars-card>

@Element("profile-chars-card", "/assets/views/profile.html")
@Dependencies(GtBox, GtButton, BnetThumb, GtTimeago)
class ProfileCharsCard extends PolymerElement {
	@Inject
	private roster: RosterService;

	@Property public id: number;
	@Property public editable: boolean
	@Property public char: Char;

	private update_pending = false;

	@Property({ computed: "char.last_update update_pending" })
	public get updatable(): boolean {
		if (this.update_pending) return false;
		let dt = Date.now() - this.char.last_update;
		return dt > 1000 * 60 * 15;
	}

	// Role
	private SetRole(role: string) {
		if (this.char.role == role) return;
		this.roster.changeRole(this.id, role);
	}

	@Listener("role-tank.click") public SetRoleTank() {
		this.SetRole("TANK");
	}

	@Listener("role-healing.click") public SetRoleHealing() {
		this.SetRole("HEALING");
	}

	@Listener("role-dps.click") public SetRoleDPS() {
		this.SetRole("DPS");
	}

	// Control buttons
	@Listener("btn-disable.click") public Disable() {
		this.roster.disableChar(this.id);
	}

	@Listener("btn-enable.click") public Enable() {
		this.roster.enableChar(this.id);
	}

	@Listener("btn-main.click") public Promote() {
		this.roster.promoteChar(this.id);
	}

	@Listener("btn-remove.click") public Remove() {
		this.roster.removeChar(this.id);
	}

	@Listener("btn-update.click")
	public async Update() {
		this.update_pending = true;
		try {
			await this.roster.updateChar(this.id);
		} finally {
			this.update_pending = false;
		}
	}
}

///////////////////////////////////////////////////////////////////////////////
// <profile-add-char>

@Element("profile-add-char", "/assets/views/profile.html")
@Dependencies(GtBox, GtForm, GtInput)
export class ProfileAddChar extends PolymerElement {
	@Inject
	private profile: ProfileService;

	// The owner of the newly added char
	@Property public owner: number = this.app.user.id;

	private server: string;
	private name: string;
	private role: string;

	private char: Char;
	private load_done = false;
	private inflight = false;

	// Check if the user can load a character (required fields filled and not already loaded)
	@Property({ computed: "server name load_done" })
	private get canLoad(): boolean {
		return !!this.server && !!this.name && !this.load_done;
	}

	// Load the character
	private async load() {
		if (!this.canLoad) return;
		this.load_done = true;

		let input: GtInput = this.$.name;
		input.error = null;

		try {
			// Check if the character is already registered to someone in the database
			let available = await this.profile.checkAvailability(this.server, this.name);
			if (!available) {
				throw new Error("This character is already registered");
			}

			// Fetch the char from Battle.net
			let char = await this.profile.fetchChar(this.server, this.name);

			// Change the dialog background
			let img = document.createElement("img");
			let background: HTMLDivElement = this.$.background;
			img.src = "http://eu.battle.net/static-render/eu/" + char.thumbnail.replace("avatar", "profilemain");
			Polymer.dom(background).appendChild(img);
			img.onload = () => {
				this.char = char;
				this.role = char.role;
				img.classList.add("loaded");
			};
		} catch (e) {
			input.error = e.message;
			input.value = "";
			input.focus();
			this.load_done = false;
		}
	}

	// Role selected
	private roleClicked(e: MouseEvent) {
		let img = <HTMLImageElement> e.target;
		this.role = img.getAttribute("role");
	}

	// Show the add-char dialog
	public show() {
		// Reset fields values
		this.name = "";
		this.server = "";
		this.char = null;
		this.load_done = false;

		// Remove old backgrounds
		let background: HTMLDivElement = this.$.background;
		let imgs = background.querySelectorAll<HTMLImageElement>("img:not([default])");
		for (let i = 0; i < imgs.length; i++) {
			imgs[i].remove();
		}

		// Show the dialog
		this.$.dialog.show();

		// Focus the server field
		this.$.name.focus();
	}

	@Property({ computed: "char inflight" })
	private get confirmDisabled(): boolean {
		return !this.char || this.inflight;
	}

	// Add the character to the user account
	private async confirm() {
		let server = this.$.server.value;
		let name = this.$.name.value;
		let role = this.role;
		let owner = this.owner;

		this.inflight = true;
		try {
			await this.profile.registerChar(server, name, role, owner);
			this.close();
		} catch (e) {
			let input: GtInput = this.$.name;
			input.error = e.message;
			input.value = "";
			input.focus();
			this.load_done = false;
		} finally {
			this.inflight = false;
		}
	}

	// Close the dialog
	public close() {
		this.$.dialog.hide();
	}
}

///////////////////////////////////////////////////////////////////////////////
// <profile-chars>

@Element("profile-chars", "/assets/views/profile.html")
@Dependencies(GtBox, GtButton, GtDialog, ProfileCharsCard, ProfileAddChar)
class ProfileChars extends PolymerElement {
	@Inject
	@On({
		"user-updated": "UserUpdated",
		"char-updated": "CharUpdated",
		"char-deleted": "CharUpdated"
	})
	private roster: RosterService;

	@Property({ observer: "update" })
	public user: number;

	@Property({ computed: "user" })
	private get me(): boolean {
		return this.user == this.app.user.id;
	}

	@Property({ computed: "user" })
	private get editable(): boolean {
		return this.app.user.id == this.user || this.app.user.promoted;
	}

	@Property public chars: number[];

	private UserUpdated(user: User) {
		if (user.id == this.user) this.update();
	}

	private CharUpdated(char: Char) {
		if (char.owner == this.user) this.update();
	}

	@throttled private update() {
		if (!this.user) return;
		this.chars = this.roster.getUserCharacters(this.user, true);
	}

	private AddChar() {
		this.$.addchar.show();
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-profile>

@View("profile", ProfileTabs)
@Element("gt-profile", "/assets/views/profile.html")
@Dependencies(ProfileUser, ProfileInfos, ProfileChars)
export class GtProfile extends PolymerElement {
	@Property
	public user: number = this.app.user.id;
}
