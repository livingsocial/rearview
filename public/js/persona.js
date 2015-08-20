var unauth = "/unauthorized";
var loggedout = false;

function signin(){
	navigator.id.request();
}

function signout(){
	navigator.id.logout();
}

function showAddUser(){
	window.location = "/adduser";
}

function cancelAdding(){
	window.history.back();
}

function validateEmail(email) {
    var re = /^([\w-]+(?:\.[\w-]+)*)@((?:[\w-]+\.)*\w[\w-]{0,66})\.([a-z]{2,6}(?:\.[a-z]{2})?)$/i;
    return re.test(email);
}

function addUser(){
	var email = document.getElementById("email").value;
	var first = document.getElementById("first").value;
	var last = document.getElementById("last").value;
	
	if(!validateEmail(email)){
		document.getElementById("hint").innerHTML = "Invalid email!";
	} else if(first==""){
		document.getElementById("hint").innerHTML = "Please enter a first name!";
	}  else if(last==""){
		document.getElementById("hint").innerHTML = "Please enter a last name!";
	} else{
		window.location = "/adduser/"+email+"/"+first+"/"+last;
	}
}

navigator.id.watch({
	loggedInUser: null,
	onlogin: function(assertion){
		var path = window.location.pathname;
		console.log("path = "+path+"; unauth = "+unauth);
		if(path == unauth || path == unauth+"/logout"){
			window.location = "/verify/" + assertion;
		}
	},
	onlogout: function(){
		console.log("Signed out!");
		if(window.location.pahname != unauth && !loggedout){
			window.location = unauth;
		}
	}
});

if(window.location.pathname == unauth + "/logout"){
	loggedout = true;
	document.getElementById("hint").style.color = "#dd0000";
	document.getElementById("hint").innerHTML = "You are not authorized!";
	signout();
}