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

    var ApplicationView = BaseView.extend({

        initialize : function(options) {
            var self = this;
            _.bindAll(this);

            self.templar        = options.templar;
            self.intervalLength = ( options.intervalLength ) ? options.intervalLength : 60; // seconds
            self.render();
        },

        render : function() {
            var self = this;

            self.templar.render({
                path   : 'application',
                el     : self.$el,
                append : true,
                data   : {
                    'application' : self.model.toJSON()
                }
            });

            self.$applicationTile = self.$el.find('.application' + self.model.get('id'));

            self.$applicationTile.find('.front').click(function() {
                location.href = '#dash/' + self.model.get('id');
            });

            self.$applicationTile.find('.settings').click(self.settings);
            self.$applicationTile.find('.cancel').click(self.settings);
            self.$applicationTile.find('.save').click(self.save);

            self.startStatusCheck();
        },

        startStatusCheck : function() {
            var self = this;

            self.updateTile();

            self.interval = setInterval(function() {
                self.updateTile();
            }, self.intervalLength * 1000);

            clearInterval(self.interval);
        },

        updateTile : function() {
            var self = this;

            $.ajax({
                url      : '/applications/' + self.model.get('id') + '/jobs',
                success  : function(response) {
                    self.checkErrorState(response);
                }
            });
        },

        checkErrorState : function(response) {
            var self                  = this,
                applicationErrorState = false; 

            for (var i = response.length - 1; i >= 0; i--) {
                if ( response[i].status != 'success' && typeof response[i].status != 'undefined' && response[i].active ) {
                    applicationErrorState = true;
                    break;
                }
            };

            if ( applicationErrorState ) {
                self.$el.find('.application' + self.model.get('id')).addClass('red');
            } else {
                self.$el.find('.application' + self.model.get('id')).removeClass('red');
            }
        },

        settings : function(e) {
            self = this;
            e.stopPropagation();

            self.$applicationTile.toggleClass('flipped');
        },

        save : function(e) {
            self = this;

            self.model.save({
                'name' : self.$applicationTile.find('.application-name').val()
            },{
                success : function(response) {
                    Backbone.Mediator.pub('view:application:save', {
                        'model'     : self.model,
                        'message'   : "Your changes to '" + self.model.get('name') + "' application were saved.",
                        'attention' : 'Application Saved!'
                    });

                    self.$applicationTile.find('p').html(self.model.get('name'));
                    self.$applicationTile.toggleClass('flipped');
                },
                error : function(model, xhr, options) {
                    Backbone.Mediator.pub('view:application:save', {
                        'model'     : self.model,
                        'message'   : "The application '" + model.get('name') + "' produced an error during the process of saving.",
                        'attention' : 'Application Save Error!',
                        'status'    : 'error'
                    });
                }
            });
        },

        destructor : function() {
            var self = this;

            // go ahead and clear out the graph updating interval
            clearInterval(self.interval);

            self.remove();
            self.$el.remove();
            self.off();

            // remove model bound events
            self.model.off();
        }
    });

    return ApplicationView;
});