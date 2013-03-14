// load the user backbone model and test...
define([
    'js/model/user'
],
function( 
    User 
) {
    // describe the test suite for this module.
    describe("The User represents a user in the system.", function() {

        // create our test user model.
        var user = new User();

        it("should be defined", function() {
            expect( user ).toBeDefined();
        });

        describe("The User object should have the following defined attributes.", function() {

            it("should have a defined url route /user", function() {
                expect( user.url ).toEqual('/user');
            });

        });    

        describe("The User object should have the following defined backbone attributes.", function() {

            it("should have an id", function() {
                expect( user.get('id') ).toBeDefined();
            });

            it("should have an email", function() {
                expect( user.get('email') ).toBeDefined();
            });

            it("should have a firstName", function() {
                expect( user.get('firstName') ).toBeDefined();
            });

            it("should have a lastName", function() {
                expect( user.get('lastName') ).toBeDefined();
            });

            it("should have a lastLogin", function() {
                expect( user.get('lastLogin') ).toBeDefined();
            });

        });
    });
});