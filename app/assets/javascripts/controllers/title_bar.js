GuildTools.controller("TitleBarCtrl", function($scope) {
	$scope.titleMenu = [
		{
			icon: "awe-help", text: "About",
			action: function() {
				$scope.breadcrumb.push("/about");
			},
			order: 1
		},
		{
			icon: "awe-cog", text: "Settings",
			action: function() {
				$scope.breadcrumb.push("/settings");
			},
			order: 2
		},
		{ separator: true, order: 2.5 },
		{
			icon: "awe-arrows-cw", text: "Reload application",
			action: function() {
				ga('send', 'event', 'app-menu', 'action', 'reload');
				location.reload();
			},
			order: 3
		},
		{
			icon: "awe-logout", text: "Logout",
			action: function() {
				$.exec("auth:logout", function() {
					ga('send', 'event', 'app-menu', 'action', 'logout', {
						hitCallback: function() {
							$.error = function() {
							};
							localStorage.removeItem("session.token");
							location.reload();
						}
					});
				});
			},
			order: 10
		}
	];

	if (!$$) {
		$scope.titleMenu.push({
			icon: "awe-download", text: "Download client", action: function() {
				ga('send', 'event', 'app-menu', 'action', 'download-client');
				window.open("/tools.exe", "_blank");
			}, order: 5
		});
	} else {
		$scope.titleMenu.push({
			icon: "awe-wrench", text: "Developer Tools", action: function() {
				ga('send', 'event', 'app-menu', 'action', 'dev-tools');
				$$.win.openDevTools();
			}, order: 5
		});
	}
});
