// load the edit monitor view and test...
define([
    'js/view/base'
],
function( 
    BaseView
) {
    // describe the test suite for this module.
    describe("The Base View (extended Backbone.View) is a collection of common view methods for other views that extend it.", function() {

        // create our test base view
        var baseView = new BaseView();

        it("should have a method defined parseAlertKeys()", function() {
            expect( baseView.parseAlertKeys ).toBeDefined();
        });

        var testAlertKeys = {
            null                   : "null values",                   
            undefined              : "undefined values",            
            0                      : "falsey integer values",  
            1                      : "truthy integer values",  
            "test@test.com"        : "single email addresses",                
            "apple"                : "single pseudo pager duty keys",      
            "test@test.com apple"  : "combinations with space",     
            "test@test.com,apple"  : "combinations with comma",     
            "test@test.com\napple" : "combinations with new line",
            " test@test.com\napple orange\nbanana " : "combinations with new line, commas, and spaces"
        };

        var testAlertKeysExpectedResults = {
            null                   : ["null"],                   
            undefined              : ["undefined"],
            0                      : ["0"],
            1                      : ["1"],
            "test@test.com"        : ["test@test.com"],
            "apple"                : ["apple"],
            "test@test.com apple"  : ["test@test.com", "apple"],
            "test@test.com,apple"  : ["test@test.com", "apple"],     
            "test@test.com\napple" : ["test@test.com", "apple"],
            " test@test.com\napple orange\nbanana " : ['test@test.com', 'apple', 'orange', 'banana']
        };

        describe("The method parseAlertKeys() parses strings and...", function() {
            for (var key in testAlertKeys) {
                if ( testAlertKeys.hasOwnProperty(key) ) {
                    (function(testValue, description, result) {

                        it("should handle " + description + " ( " + testValue + " )", function() {
                            expect( baseView.parseAlertKeys(testValue) ).toEqual( result );
                        });
                      
                    })(key, testAlertKeys[key], testAlertKeysExpectedResults[key]);
                }
            }
        });
    });
});