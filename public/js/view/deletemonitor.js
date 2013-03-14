define([
    'jquery',
    'underscore',
    'backbone',
    'handlebars',
    'view/base',
    'backbone-mediator'
], function(
    $,
    _,
    Backbone,
    Handlebars,
    BaseView
){

    var DeleteMonitorView = BaseView.extend({

        events: {
            'click button.cancel' : 'closeModal',
            'click button.delete' : 'delete'
        },

        initialize : function(options) {
            var self     = this;
            self.templar = options.templar;

            self.render();
        },

        render : function() {
            var self = this;

            self.templar.render({
                path : 'deletemonitor',
                el   : self.$el,
                data : {}
            });

            self.modalEl = self.$el.find('.delete-monitor');
            // resize add applciation modal to fit screen size
            self.resizeModal($('#deleteMonitor'), 'small', true);
        },

        /**
         * DeleteMonitorView#delete()
         *
         **/
        delete : function() {
            var self = this;

            self.closeModal();
            Backbone.Mediator.pub('view:deletemonitor:delete');
        },

        closeModal : function() {
            var self = this;
            self.modalEl.modal('hide');
        },

        destructor : function() {
            var self          = this,
                prevSiblingEl = self.$el.prev();

            self.remove();
            self.unbind();
            if (self.onDestruct) {
                self.onDestruct();
            }

            // containing element in server side template is removed for garbage collection,
            // so we are currently putting a new one in it's place after this process
            self.$el = $("<section class='delete-monitor-wrap'></section>").insertAfter(prevSiblingEl);
        }
    });

    return DeleteMonitorView;
});