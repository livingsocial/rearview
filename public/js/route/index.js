define([
    'backbone',
    'backbone-mediator'
],
function(
    Backbone
){

    var IndexRouter = Backbone.Router.extend({

        initialize : function(options) {
            var self = this;
            self.app = options.app;
        },

        routes : {
            ''                      : 'ecosystem',
            'ecosystem'             : 'ecosystem',
            'dash/:aid'             : 'dashboard',  // application id
            'dash/:aid/expand/:mid' : 'dashboard'
        },

        dashboard : function(aid, mid) {
            var self = this;
            // make sure we capture an integer value
            aid = parseInt(aid);

            if (aid) {
                self.app.dashboard(aid, mid);
            }
            Backbone.Mediator.pub('router:index:dashboard', self);
        },

        ecosystem : function() {
            var self = this;

            self.app.ecosystem();
        }
    });

    return IndexRouter;
});