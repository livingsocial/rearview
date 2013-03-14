define([
    'jquery',     
    'underscore', 
    'backbone',
    'handlebars',
    'view/base',
    'highcharts',
    'highcharts-gray',
    'backbone-mediator',
    'xdate'
], function(
    $, 
    _, 
    Backbone,
    Handlebars,
    BaseView,
    HighChart
){  

    var HeaderView = BaseView.extend({
      
        initialize : function(options) {
            var self = this;

            self.templar = options.templar;

            self.currentNav = {
                'header' : {
                    'title'    : 'Ecosystem',
                    'subtitle' : 'Rearview Applications',
                    'date'     : new XDate(Date.now()).toUTCString("MMM dd, yyyy"),
                    'nav'      : {
                        'back' : false
                    }
                }
            };

            _.bindAll(self); 

            Backbone.Mediator.sub('view:ecosystem:render', self.render, self);
            Backbone.Mediator.sub('view:dashboard:render', self.render, self);

            self.render();
        },

        render : function(data) {
            var self = this;

            self.previous = self.currentNav;
            _.extend(self.currentNav.header, data);

            self.templar.render({
                path : 'header',
                el   : self.$el,
                data : self.currentNav
            });
        }
    });

    return HeaderView;
});