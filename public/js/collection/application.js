define(
[
    'jquery',
    'underscore',
    'backbone',
    'model/application'
],
function(
    $,
    _,
    Backbone,
    ApplicationModel
) {

    /**
     * ApplicationCollection
     *
     * Route that pulls back all available applications.
     **/
    var ApplicationCollection = Backbone.Collection.extend({
        model : ApplicationModel,
        url   : '/applications',

        comparator : function(application) {
            return application.get('name').toLowerCase();
        },

        initialize : function(models, options) {
            var self = this;
            _.bindAll(self);
            
            if (options) {
                self.cb = options.cb;
            }
            
            self.fetch({
                success : function() {
                    if ( self.cb ) {
                        self.cb();
                    }
                    Backbone.Mediator.pub('collection:application:init', self);
                },
                async : false
            });
        }
    });

    return ApplicationCollection;
});