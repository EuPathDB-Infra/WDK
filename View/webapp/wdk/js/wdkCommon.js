// =============================================================================
// the common functions provided by wdk
// =============================================================================

// On all pages, check that cookies are enabled.
jQuery(document).ready(function() {
	var testCookieName = 'wdkTestCookie';
	var testCookieValue = 'test';
	var wdk = new WDK();

        window.wdk = wdk;

	wdk.createCookie(testCookieName,testCookieValue,1);
	var test = wdk.readCookie(testCookieName);
	if (test == 'test') {
		wdk.eraseCookie(testCookieName);
	}
	else {
		jQuery.blockUI({message: "<div><h2>Cookies are disabled</h2><p>This site requires cookies.  Please enable them in your browser preferences.</p><input type='submit' value='OK' onclick='jQuery.unblockUI();' /></div>", css: {position : 'absolute', backgroundImage : 'none'}});
	}
});


function WDK() {

    // -------------------------------------------------------------------------
    // cookie handling methods
    // -------------------------------------------------------------------------
    this.createCookie = function(name,value,days) {
    	if (days) {
    		var date = new Date();
    		date.setTime(date.getTime()+(days*24*60*60*1000));
    		var expires = "; expires="+date.toGMTString();
    	}
    	else var expires = "";
    	document.cookie = name+"="+value+expires+"; path=/";
    };

    this.readCookie = function(name) {
    	var nameEQ = name + "=";
    	var ca = document.cookie.split(';');
    	for(var i=0;i < ca.length;i++) {
    		var c = ca[i];
    		while (c.charAt(0)==' ') c = c.substring(1,c.length);
    		if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length,c.length);
    	}
    	return null;
    };

    this.eraseCookie = function(name) {
    	this.createCookie(name,"",-1);
    }

    // ------------------------------------------------------------------------
    // Event registration & handling code. The proper event will be invoked 
    // during the page loading of the assign type. For example, question events
    // will be invoked on the loading of stand-alone question page, and the
    // loading of question page in the add/revise step popup box.
    // ------------------------------------------------------------------------
    this.questionEvents = new Array();
    this.resultEvents = new Array();
    this.recordEvents = new Array();

    this.registerQuestionEvent = function(handler) {
        this.questionEvents.push(handler);
    }

    this.registerResultEvent = function(handler) {
        this.resultEvents.push(handler);
    }

    this.registerRecordEvent = function(handler) {
        this.recordEvents.push(handler);
    }

    this.onloadQuestion = function() {
        for (var handler in this.questionEvents) {
            handler();
        }
    }
}



function uncheckFields(notFirst) {
    var form = document.downloadConfigForm;
    var cb = form.selectedFields;
    if (notFirst) {
        for (var i=1; i<cb.length; i++) {
            if (cb[i].disabled) continue;
            cb[i].checked = null;
        }
    } else {
        cb[0].checked = null;
    }
}

function checkFields(all) {
    var form = document.downloadConfigForm;
    var cb = form.selectedFields;
    cb[0].checked = (all > 0 ? null : 'checked');
    for (var i=1; i<cb.length; i++) {
        if (cb[i].disabled) continue;
        cb[i].checked = (all > 0 ? 'checked' : null);
    }
}

function chooseAll(bool, form, node) {
    if (form[node].type == 'select-multiple') {
      multiSelectAll(bool, form, node);
    } else {
      checkAll(bool, form, node);
    }
}

function checkAll(bool, form, node) {
    var cb = form[node];//document.getElementsByName(node);
    for (var i=0; i<cb.length; i++) {
         if (cb[i].disabled) continue;
	 if(bool && cb[i].checked == false) cb[i].click();
         if(!bool && cb[i].checked == true) cb[i].click();
    }
}
