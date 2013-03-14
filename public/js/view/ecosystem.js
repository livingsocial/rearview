define([
    'jquery',
    'underscore',
    'backbone',
    'handlebars',
    'view/base',
    'view/application',
    'backbone-mediator',
    'xdate'
], function(
    $,
    _,
    Backbone,
    Handlebars,
    BaseView,
    ApplicationView
){

    var EcosystemView = BaseView.extend({
        applications : [],
        initialize : function(options) {
            var self = this;
            _.bindAll(this);

            self.templar = options.templar;

            Backbone.Mediator.sub('view:dashboard:render', self.destructor, self);
            Backbone.Mediator.sub('view:addapplication:save', self.update, self);
            Backbone.Mediator.sub('view:application:save', self.update, self);

            self.applications = [];
        },

        render : function() {
            var self = this;
            
            self.collection.each(function( application ) {
                self.applications.push(new ApplicationView({
                    'el'      : self.el,
                    'model'   : application,
                    'templar' : self.templar
                }));
            });

            Backbone.Mediator.pub('view:ecosystem:render', {
                'title'    : 'Ecosystem',
                'subtitle' : 'Rearview Applications',
                'nav' : {
                    'ecosystem' : true,
                    'dashboard' : false
                }
            });
        },

        update : function(data) {
            var self             = this,
                applicationModel = ( data && data.model )
                                 ? data.model 
                                 : null;

            self.$el.empty();
            self.render();
        },

        destroyApplications : function() {
            var self = this;

            for (viewName in self.applications) {
                var view = self.applications[viewName];
                view.destructor();
                delete self.applications[viewName];
            }
        },

        destructor : function() {
            var self          = this,
                prevSiblingEl = self.$el.prev();

            self.off();
            self.unbind();
            self.collection.off();
            self.remove();

            Backbone.Mediator.unsubscribe('view:dashboard:render', self.destructor, self);
            Backbone.Mediator.unsubscribe('view:addapplication:save', self.update, self);
            Backbone.Mediator.unsubscribe('view:application:save', self.update, self);

            self.destroyApplications();

            prevSiblingEl.after("<section class='ecosystem-application-wrap clearfix'>");
        }
    });

    return EcosystemView;
});