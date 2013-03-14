define([
    'jquery',     
    'underscore', 
    'backbone',
    'handlebars',
    'view/base',
    'backbone-mediator',
    'xdate'
], function(
    $, 
    _, 
    Backbone,
    Handlebars,
    BaseView
){  

    var PrimaryNavView = BaseView.extend({

        initialize : function(options) {
            var self = this;
            self.templar = options.templar;
            self.user    = options.user;
            _.bindAll(self);

            Backbone.history.bind("all", function (route, router) {
                self.setNav();
            });

            Backbone.Mediator.sub('view:application:save', self.render, self);
            Backbone.Mediator.sub('view:addapplication:save', self.render, self);
        },

        render : function() {
            var self = this;
            self.$el.empty();
            self.collection.sort();

            self.templar.render({
                path : 'primarynav',
                el   : self.$el,
                data : {
                    'nav'  : self.collection.toJSON(),
                    'user' : self.user.toJSON()
                }
            });

            self.$el.find('.user').tooltip({
                placement : 'bottom'
            });
        },

        setNav : function() {
            var self     = this,
                fragment = Backbone.history.fragment;

            self.$el.find('li').removeClass('active');

            switch (fragment) {
                case 'ecosystem':
                    self.$el.find('.nav > li:first-child').addClass('active');
                    break;
            }
        },
        destructor : function() {
            var self = this;
            Backbone.Mediator.unsubscribe('view:application:save', self.render, self);
            Backbone.Mediator.unsubscribe('view:addapplication:save', self.render, self);
        }
    });

    return PrimaryNavView;
});