// load the application backbone model and test...
define([
    'js/model/application'
],
function( 
    Application 
) {
    // describe the test suite for this module.
    describe("The application model represents an application in the system.", function() {

        // create our test application model.
        var application = new Application();

        beforeEach(function() {
            application = new Application();
        });

        it("The application object should be defined.", function() {
            expect( application ).toBeDefined();
        });

        describe("The application object should have the following defined backbone attributes.", function() {
            it("should have an id", function() {
                expect( application.get('id') ).toBeDefined();
            });

            it("should have a userId", function() {
                expect( application.get('userId') ).toBeDefined();
            });

            it("should have a name", function() {
                expect( application.get('name') ).toBeDefined();
            });
        });

        describe("The application object should have the following defined attributes.", function() {
            it("should have a defined url route /applications when id is not defined", function() {
                expect( application.get('id') ).toBeNull()
                expect( application.url() ).toEqual('/applications');
            });
        });  

        describe("The application object should have the following defined attributes after initialize.", function() {
            it("should have a defined url route /applications/# when id is defined", function() {
                // populate the application object
                application.set({
                    'id'     : 3,
                    'userId' : 1,
                    'name'   : 'Cylon'
                }); 

                expect( application.get('id') ).not.toBeNull();
                expect( application.get('id') ).toEqual(3);
                expect( application.get('userId') ).toEqual(1);
                expect( application.get('name') ).toEqual('Cylon');
                expect( application.url() ).toEqual('/applications/3');
            });
        }); 
    });
});