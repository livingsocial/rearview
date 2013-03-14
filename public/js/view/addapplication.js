define([
    'jquery',
    'underscore',
    'backbone',
    'handlebars',
    'view/base',
    'model/application',
    'codemirror',
    'xdate',
    'codemirror-ruby',
    'jquery-validate'
], function(
    $,
    _,
    Backbone,
    Handlebars,
    BaseView,
    ApplicationModel,
    CodeMirror
){

    var AddApplicationView = BaseView.extend({

        events: {
            'click .setApplication'  : 'checkValidation',
            'hidden #addApplication' : '_cleanUp'
        },

        initialize : function(options) {
            var self     = this;
            self.user    = options.user;
            self.templar = options.templar;
        },

        render : function() {
            var self = this;

            self.templar.render({
                path : 'addapplication',
                el   : self.$el,
                data : {}
            });

            self.setAddApplicationValidation();
            self.modalEl = self.$el.find('.add-application');

            // resize add applciation modal to fit screen size
            self.resizeModal($('#addApplication'), 'small', true);
            
        },
        /**
         * AddApplicationView#setAddApplicationValidation()
         *
         * Sets up the front end form validation for the name field which is required.
         * If name is present, save the sceduling data to the job model and setup the
         * next view in the add application workflow to set up the metrics data.
         **/
        setAddApplicationValidation : function() {
            var self = this;

            // grab form data
            self.form = $('#addApplicationForm');

            // set up form validation
            self.validator = self.form.validate({
                rules : {
                    'applicationName' : {
                        'required' : true
                    }
                },
                highlight : function(label) {
                    $(label).closest('.control-group').addClass('error');
                },
                success : function(label) {
                    label.closest('.control-group').removeClass('error');
                    $(label).remove();
                },
                submitHandler : function(form) {
                    self.saveFinish();
                }
            });
        },
        checkValidation : function() {
            var self = this;

            // validate form
            this.form.submit();
        },
        /**
         * AddApplicationView#saveFinish()
         *
         * Save the current model and close the modal dialogue.
         **/
        saveFinish : function() {
            var self = this;

            self._saveApplication(function() {
                self._closeModal();
            });
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
            self.$el = $("<section class='add-application-wrap'></section>").insertAfter(prevSiblingEl);
        },

        /*
         * PSEUDO-PRIVATE METHODS (internal)
         */

        /** internal
         * AddApplicationView#_saveApplication(cb)
         * - cb (Function): method to be called after application saved.
         *
         * Post new model to the POST service route.
         **/
        _saveApplication : function(cb) {
            var self = this;

            self.model.save({
                'userId' : self.user.get('id'),
                'name'   : self.$el.find('#applicationName').val()
            },
            {
                success : function(model, response, options) {
                    self.collection.add(model);

                    if ( typeof cb === 'function' ) {
                        cb();
                    }

                    Backbone.Mediator.pub('view:addapplication:save', {
                        'model'     : model,
                        'message'   : "The application '" + model.get('name') + "' was added.",
                        'attention' : 'Application Saved!'
                    });

                    self._closeModal();
                },
                error : function(model, xhr, options) {
                    self.validator.showErrors({'applicationName' : 'That application name already exists!'});
                }
            });
        },
        /** internal
         * AddApplicationView#_closeModal()
         *
         * Call hide on the modal initialized to a saved DOM element.
         **/
        _closeModal : function() {
            var self = this;

            self._cleanUp();
            self.modalEl.modal('hide');
        },

        _cleanUp : function() {
            var self = this;
            self.$el.find('#applicationName').val('');
            self.$el.find('#applicationName').parent().removeClass('error');
            self.validator.resetForm();
            self.model = new ApplicationModel();
        }
    });

    return AddApplicationView;
});