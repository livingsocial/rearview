// load the job backbone model and test...
define([
    'js/model/job'
],
function(
    Job
) {
    // describe the test suite for this module.
    describe("The job model represents a job (monitor) in the system.", function() {

        // create our test job model.
        var job = new Job();

        beforeEach(function() {
            job = new Job();
        });

        it("The job object should be defined.", function() {
            expect( job ).toBeDefined();
        });

        describe("The job object should have the following defined backbone attributes.", function() {
            it("should have an id attribute", function() {
                expect( job.get('id') ).toBeDefined();
                expect( job.get('id') ).toBeNull();
            });

            it("should have an appId attribute", function() {
                expect( job.get('appId') ).toBeDefined();
                expect( job.get('appId') ).toEqual(1);
            });

            it("should have an userId attribute", function() {
                expect( job.get('userId') ).toBeDefined();
                expect( job.get('userId') ).toBeNull();
            });

            it("should have a name attribute", function() {
                expect( job.get('name') ).toBeDefined();
                expect( job.get('name') ).toEqual(jasmine.any(String));
            });

            it("should have an active attribute", function() {
                expect( job.get('active') ).toBeDefined();
                expect( job.get('active') ).toBeTruthy();
            });

            it("should have a jobType attribute", function() {
                expect( job.get('jobType') ).toBeDefined();
                expect( job.get('jobType') ).toEqual(jasmine.any(String));
            });

            it("should have a version attribute", function() {
                expect( job.get('version') ).toBeDefined();
                expect( job.get('version') ).toEqual(jasmine.any(Number));
            });

            it("should have a alertKeys attribute", function() {
                expect( job.get('alertKeys') ).toBeDefined();
                expect( job.get('alertKeys') ).toEqual(jasmine.any(Array));
            });

            it("should have a cronExpr attribute", function() {
                expect( job.get('cronExpr') ).toBeDefined();
                expect( job.get('cronExpr') ).toEqual(jasmine.any(String));
            });

            it("should have an errorTimeout attribute", function() {
                expect( job.get('errorTimeout') ).toBeDefined();
                expect( job.get('errorTimeout') ).toEqual(jasmine.any(Number));
            });

            it("should have a minutes attribute", function() {
                expect( job.get('minutes') ).toBeDefined();
                expect( job.get('minutes') ).toEqual(jasmine.any(Number));
            });

            it("should have a metrics attribute", function() {
                expect( job.get('metrics') ).toBeDefined();
                expect( job.get('metrics') ).toEqual(jasmine.any(Array));
            });

            it("should have a monitorExpr attribute", function() {
                expect( job.get('monitorExpr') ).toBeDefined();
                expect( job.get('monitorExpr') ).toEqual(jasmine.any(String));
            });
        });

        describe("The job object should have the following defined attributes.", function() {
            it("should have a defined url route /jobs when id is not defined", function() {
                expect( job.get('id') ).toBeNull()
                expect( job.url() ).toEqual('/jobs');
            });
        });

        describe("The job object should have the following defined attributes after initialize.", function() {
            it("should have a defined url route /jobs/# when id is defined", function() {
                // populate the job object
                job.set({
                    'id' : 3
                });

                expect( job.get('id') ).not.toBeNull();
                expect( job.get('id') ).toEqual(3);
                expect( job.url() ).toEqual('/jobs/3');
            });
        });
    });
});