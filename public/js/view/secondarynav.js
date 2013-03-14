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

    var SecondaryNavView = BaseView.extend({

        initialize : function(options) {
            var self = this;
            self.templar = options.templar;
            _.bindAll(self); 

            self.currentNav = {
                'secondary' : {
                    'nav' : {
                        'ecosystem' : true,
                        'dashboard' : false
                    }
                }
            };

            Backbone.Mediator.sub('view:dashboard:render', self.render, self);
            Backbone.Mediator.sub('controller:dashboard:init', self.render, self);          
            self.render();
        },

        render : function(data) {
            var self = this;

            self.destructor();
            self.previous = self.currentNav;
            _.extend(self.currentNav.secondary, data);

            self.templar.render({
                path : 'secondarynav',
                el   : self.$el,
                data : self.currentNav
            });
        },

        destructor : function() {
            var self = this;
            self.$el.empty();
        }
    });

    return SecondaryNavView;
});