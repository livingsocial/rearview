define(
[
    'jquery',
    'underscore',
    'backbone',
    'model/job'
],
function(
    $,
    _,
    Backbone,
    JobModel
) {

    /**
     * JobCollection
     *
     * Route that pulls back all available job monitors.
     **/
    var JobCollection = Backbone.Collection.extend({
        model : JobModel,
        url   : function() {
            var self = this,
                serviceRoute = ( self.appId ) ? '/applications/' + self.appId + '/jobs' : '/jobs';
            return serviceRoute;
        },
        comparator : function(job) {
            return job.get('name').toLowerCase();
        },
        initialize : function(models, options) {
            var self = this;
            _.bindAll(self);

            self.appId = options.appId;
            self.cb    = options.cb;

            self.fetch({
                'success' : function() {
                    if ( self.cb ) {
                        self.cb();
                    }
                },
                async : false
            });
        }
    });

    return JobCollection;
});

