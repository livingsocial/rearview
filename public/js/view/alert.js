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

    var AlertView = BaseView.extend({

        subscriptions : {
            'view:addapplication:save' : 'render',
            'view:addmonitor:save'     : 'render',
            'view:addmonitor:test'     : 'render',
            'view:editmonitor:delete'  : 'render',
            'view:editmonitor:save'    : 'render',
            'view:smallmonitor:save'   : 'render',
            'view:application:save'    : 'render'
        },

        initialize : function(options) {
            var self = this;
            _.bindAll(self);
            self.templar = options.templar;
        },

        render : function(data) {
            var self = this;

            self.templar.render({
                path   : 'alert',
                el     : self.$el,
                data   : data
            });

            self.activate();
        },

        activate : function() {
            var self = this;
            self.$el.addClass('active');
            _.delay(self.deactivate, 8000);
        },

        deactivate : function() {
            var self = this;
            self.$el.removeClass('active');
        }
    });

    return AlertView;
});