define([
    'jquery',
    'underscore',
    'backbone',
    'handlebars',
    'view/base',
    'model/job',
    'codemirror',
    'xdate',
    'codemirror-ruby',
    'jquery-validate',
    'parsley',
    'backbone-mediator'
], function(
    $,
    _,
    Backbone,
    Handlebars,
    BaseView,
    JobModel,
    CodeMirror
){

    var AddMonitorView = BaseView.extend({
        scheduleViewInitialized : false,
        metricsViewInitialized  : false,
        scheduleView            : true,

        el : '.add-monitor-wrap',

        events: {
            'click .setMetrics'   : 'advanceToMetrics',
            'click .testGraph'    : 'testMetrics',
            'click .nameSchedule' : 'backToSchedule',
            'click .saveFinish'   : 'saveFinish',
            'click .back'         : 'exitFullScreen',
            'hidden #addMonitor'  : 'modalClose',
            'show #addMonitor'    : 'modalShow'
        },

        initialize : function(options) {
            var self = this;
            _.bindAll(this);
            self.user    = options.user;
            self.appId   = options.appId;
            self.templar = options.templar;

            // use debounce to throttle resize events and set the height when
            // the viewport changes.
            var resize = _.debounce(self.adjustModalLayout, 500);

            // Add the event listener
            $(window).resize(resize);

            self.render();
        },

        render : function() {
            var self = this;

            self.setElement( $('.add-monitor-wrap') );

            self.templar.render({
                path : 'addmonitor',
                el   : self.$el,
                data : {}
            });

            self.templar.render({
                path : 'schedulemonitor',
                el   : self.$el.find('.content-wrap'),
                data : {}
            });

            self.scheduleViewInitialized = true;

            // scheduling is the first step in add monitor workflow
            self.setScheduleValidation();

            // store reference to modal
            self.modalEl = self.$el.find('.add-monitor');
            self.resizeModal($('#addMonitor'), 'large');
        },
        /**
         * AddMonitorView#setScheduleValidation()
         *
         * Sets up the front end form validation for the name field which is required.
         * If name is present, save the sceduling data to the job model and setup the
         * next view in the add monitor workflow to set up the metrics data.
         **/
        setScheduleValidation : function() {
            var self = this;

            self.scheduleForm = $('#namePagerForm');

            var validator = self.scheduleForm.parsley({
                listeners: {
                    onFormSubmit : function ( isFormValid, event, ParsleyForm ) {
                        if (isFormValid) {
                            self._setSchedule();
                            self._setupMetricsView();
                        }
                    }
                }
            });
        },
        setMetricsValidation : function() {
            var self = this;

            $.validator.addMethod('code', function(value, element) {
                var mirror  = $(element).data('CodeMirror'),
                    wrapper = $( mirror.getWrapperElement() );
                return self._validateMirror(mirror);
            }, 'This field is required.');

            $.validator.addMethod('metric-ruby', function(value, element) {
                var valid = false;

                $.ajax({
                    url   : '/monitor',
                    type  : 'post',
                    data  : JSON.stringify(self.model.toJSON()),
                    async : false,
                    success : function( response ) {
                        if ( response.status == 'success' ) {
                            valid = true;
                        }
                    }
                });
                return valid;
            }, 'Your metrics code does not validate.');

            $.validator.addMethod('expression-ruby', function(value, element) {
                var valid = false;

                $.ajax({
                    url   : '/monitor',
                    type  : 'post',
                    data  : JSON.stringify(self.model.toJSON()),
                    async : false,
                    success : function( response ) {
                        if ( response.status == 'success' || value == '') {
                            valid = true;
                        }
                    }
                });
                return valid;
            }, 'Your expression code does not validate.');

            self.metricsForm = $('#metricsExpressionsForm');

            // set up form validation
            self.metricsForm.validate({
                rules : {
                    'inputMetrics' : {
                        'code'        : true,
                        'expression-ruby' : true
                    },
                    'inputExpressions' : {
                        'expression-ruby' : true
                    }
                },
                errorPlacement: function(error, element) {
                    var mirror = $(element).data('CodeMirror');
                    if(mirror) {
                        var wrapper = $( mirror.getWrapperElement() );
                        self._validateMirror(mirror);
                        error.insertAfter(wrapper);
                    } else {
                        error.insertAfter(element);
                    }
                },
                highlight : function(label) {
                    $(label).closest('.control-group').addClass('error');
                    $(label).closest('fieldset').addClass('error');
                },
                success : function(label) {
                    $(label).closest('.control-group').removeClass('error');
                    $(label).closest('fieldset').removeClass('error');
                    $(label).remove();
                },
                submitHandler : function(form) {
                    self._saveMonitor(function() {
                        self._closeModal();
                    });
                }
            });
        },
        /**
         * AddMonitorView#testMetrics()
         *
         * Set scheduling data to the job model and post the data to
         * the /monitor route which will return the proper graphite data.
         * Finally, format the returned data for HighCharts to consume and
         * render.
         **/
        testMetrics : function() {
            var self = this;

            self._setMetrics();

            $.post('/monitor', JSON.stringify(self.model.toJSON()), function(result) {
                if (result.graph_data) {
                    var formattedGraphData = self.formatGraphData( result.graph_data );
                    self.renderGraphData(self.chart, formattedGraphData);

                    // set the output field from the std out response
                    $('#outputView').val(result.output);
                }

                if(result.status === 'error') {
                    Backbone.Mediator.pub('view:addmonitor:test', {
                        'model'     : self.model,
                        'message'   : "The monitor '" + self.model.get('name') + "' produced an error after testing.",
                        'attention' : 'Monitor Test Error!',
                        'status'    : 'error'
                    });
                }
            })
            .error(function(result) {
                Backbone.Mediator.pub('view:addmonitor:test', {
                    'model'     : self.model,
                    'message'   : "The monitor '" + self.model.get('name') + "' produced an error after testing.",
                    'attention' : 'Monitor Test Error!',
                    'status'    : 'error'
                });
            });
        },
        /**
         * AddMonitorView#advanceToMetrics()
         *
         * Triggers form validation which on success advances to the metrics
         * view.
         **/
        advanceToMetrics : function() {
            var self = this;
            // validate form
            self.scheduleForm.parsley('validate');
        },
        /**
         * AddMonitorView#backToSchedule()
         *
         * Sets the schedule view when going backwards through the add monitor
         * workflow.
         **/
        backToSchedule : function() {
            var self = this;

            self._setupScheduleView();
        },
        /**
         * AddMonitorView#saveFinish()
         *
         * Save the current model and close the modal dialogue.
         **/
        saveFinish : function() {
            var self = this;

            self._setMetrics();

            $('#inputMetrics').css({
                'margin-left' : '-10000px',
                'left'        : '-10000px',
                'display'     : 'block',
                'position'    : 'absolute'
            });
            $('#inputExpressions').css({
                'margin-left' : '-10000px',
                'left'        : '-10000px',
                'display'     : 'block',
                'position'    : 'absolute'
            });

            self.metricsForm.submit();
        },

        modalClose : function() {
            var self = this;

            // reset addMonitorView for when modal closes
            if (self.metricsViewInitialized) {
                self.backToSchedule();
                self.metricsViewInitialized = false;
            }

            Backbone.Mediator.pub('view:addmonitor:close');
        },

        modalShow : function() {
            Backbone.Mediator.pub('view:addmonitor:show');
        },

        /**
         * AddMonitorView#destructor()
         *
         * Try and keep memory leaks from happening by cleaning up DOM,
         * nulling out references, and unbinding events. Also since this
         * view sticks around, we need to reset things such as a new model
         * for saving next time.
         **/
        destructor : function() {
            var self = this;

            self.metricsViewInitialized  = false;
            self.scheduleViewInitialized = false;
            self.metricsMonitorFooter    = null;
            self.scheduleMonitorBody     = null;
            self.scheduleMonitorFooter   = null;

            var prevSiblingEl = self.$el.prev();

            self.remove();
            self.unbind();
            if (this.onDestruct) {
                this.onDestruct();
            }
            self.off();

            // containing element in server side template is removed for garbage collection,
            // so we are currently putting a new one in it's place after this process
            $("<section class='add-monitor-wrap'></section>").insertAfter(prevSiblingEl);
        },

        /**
         * AddMonitorView#resize()
         */
        resize : function() {
            var self = this;
            return _.debounce(self.adjustModalLayout, 500);
        },

        /*
         * PSEUDO-PRIVATE METHODS (internal)
         */


        /** internal
         * AddMonitorView#_setSchedule()
         *
         * Save scheduling data to the job model.
         **/
        _setSchedule : function() {
            var self = this;
            // grab form data & update model
            self.model.set({
                'userId'        : self.user.get('id'),
                'name'          : self.$el.find('#monitorName').val(),
                'description'   : self.$el.find('#description').val(),
                'alertKeys'     : self.parseAlertKeys( self.$el.find('#pagerDuty').val() ),
                'cronExpr'      : self._createCronExpr()
            });
        },
        /** internal
         * AddMonitorView#_setMetrics()
         *
         * Save metrics data to the job model.
         **/
        _setMetrics : function() {
            var self = this;
            // grab form data & update model
            self.model.set({
                'userId'      : self.user.get('id'),
                'monitorExpr' : self.expressionsMirror.getValue(),
                'metrics'     : self.metricsMirror.getValue().split('\n'),
                'minutes'     : parseInt(self.$el.find('#minutesBack').val()),
                'toDate'      : self.$el.find('#fromDatePicker').val()
            });
        },
        /** internal
         * AddMonitorView#_setupScheduleView()
         *
         * Store reference to previous page and substitute in the scheduling form.
         **/
        _setupScheduleView : function() {
            var self = this;

            // store metrics body & footer
            self.metricsMonitorBody   = $('.add-monitor .modal-body').detach();
            self.metricsMonitorFooter = $('.add-monitor .modal-footer').detach();

            $('.add-monitor').append( self.scheduleMonitorBody );
            $('.add-monitor').append( self.scheduleMonitorFooter );

            self.scheduleView = true;
            self.adjustModalLayout();
        },
        /** internal
         * AddMonitorView#_setupMetricsView()
         *
         * Handles transition between scheduling and metrics views
         * by checking to see if we already have initialized the view,
         * otherwise initializing code entry, date picker, and graph areas.
         **/
        _setupMetricsView : function() {
            var self = this;

            var modalContainerEl = $('.add-monitor');

            if ( !self.metricsViewInitialized ) {
                self.scheduleMonitorBody   = $('.add-monitor .modal-body').detach();
                self.scheduleMonitorFooter = $('.add-monitor .modal-footer').detach();

                self.templar.render({
                    path   : 'setmetrics',
                    el     : modalContainerEl,
                    append : true,
                    data   : {}
                });

                self._initializeCodeMirror();
                self._initializeDatePicker();

                self.initGraph( modalContainerEl.find('.graph')[0] );

                self.setMetricsValidation();

                // set that metrics view has been initialized to
                // prevent initialization again
                self.metricsViewInitialized = true;
            } else {
                self.scheduleMonitorBody   = $('.add-monitor .modal-body').detach();
                self.scheduleMonitorFooter = $('.add-monitor .modal-footer').detach();

                $('.add-monitor').append( self.metricsMonitorBody );
                $('.add-monitor').append( self.metricsMonitorFooter );
            }

            self.scheduleView = false;
            self.adjustModalLayout();
        },

        adjustModalLayout : function() {
            var self               = this,
                modalEl            = $('#addMonitor'),
                sizes              = self.resizeModal(modalEl, 'large'),
                heroAdjust         = 80,    // hero unit height adjust
                testFieldSetAdjust = 150,
                graphOutputAdjust  = 80;

            // only adjust on the metrics view step
            if ( !self.scheduleView ) {
                // make all the needed height, width calculation and DOM adjustments
                modalEl.find('.hero-unit').css({
                    'min-height' : sizes.body.height - heroAdjust
                });

                self.expressionsMirror.setSize(null, ( sizes.body.height - heroAdjust - testFieldSetAdjust ) / 2);
                self.metricsMirror.setSize(null, ( sizes.body.height - heroAdjust - testFieldSetAdjust ) / 2);

                modalEl.find('.graph').css({
                    'min-height' : ( sizes.body.height - heroAdjust - graphOutputAdjust ) / 2
                });

                self.chart.setSize(null, ( sizes.body.height - heroAdjust - graphOutputAdjust ) / 2);

                modalEl.find('#outputView').css({
                    'min-height' : ( sizes.body.height - heroAdjust - graphOutputAdjust ) / 2
                });
            }
        },

        /** internal
         * AddMonitorView#_initializeCodeMirror()
         *
         * Setup code entry areas on the metrics view.
         **/
        _initializeCodeMirror : function() {
            var self                    = this,
                $expressions            = self.$el.find('#inputExpressions')[0],
                expressionsCodeSelector = '.add-monitor .expressions .CodeMirror',
                $metrics                = self.$el.find('#inputMetrics')[0],
                metricsCodeSelector     = '.add-monitor .metrics .CodeMirror',
                $closeButton            = self.$el.find('button.close'),
                $backButton             = self.$el.find('button.back');

            self.expressionsMirror = CodeMirror.fromTextArea( $expressions, {
                value        : '',
                lineNumbers  : true,
                lineWrapping : true,
                height       : '100',
                mode         : 'ruby',
                theme        : 'ambiance',
                onKeyEvent   : function(i, e) {
                    if (( e.keyCode == 70 && e.ctrlKey ) && e.type == 'keydown') {
                        e.stop();
                        return self._toggleFullscreen(expressionsCodeSelector, self.expressionsMirror, $closeButton, $backButton);
                    }
                }
            });

            $($expressions).data('CodeMirror', self.expressionsMirror);

            self.metricsMirror = CodeMirror.fromTextArea( $metrics, {
                value        : '',
                lineNumbers  : true,
                lineWrapping : true,
                mode         : 'ruby',
                theme        : 'ambiance',
                onKeyEvent   : function(i, e) {
                    if (( e.keyCode == 70 && e.ctrlKey ) && e.type == 'keydown') {
                        e.stop();
                        return self._toggleFullscreen(metricsCodeSelector, self.metricsMirror, $closeButton, $backButton);
                    }
                }
            });

            $($metrics).data('CodeMirror', self.metricsMirror);
        },
        exitFullScreen : function(e) {
            var self               = this,
                $closeButton       = self.$el.find('button.close'),
                $backButton        = self.$el.find('button.back'),
                $metricsEditor     = $('.add-monitor .metrics .CodeMirror'),
                $expressionsEditor = $('.add-monitor .expressions .CodeMirror');

            $closeButton.show();
            $backButton.hide();

            $metricsEditor.removeClass('fullscreen');
            if ( $metricsEditor.data('beforeFullscreen') ) {
                $metricsEditor.height($metricsEditor.data('beforeFullscreen').height);
                $metricsEditor.width($metricsEditor.data('beforeFullscreen').width);
            }
            self.metricsMirror.refresh();

            $expressionsEditor.removeClass('fullscreen');
            if ( $expressionsEditor.data('beforeFullscreen') ) {
                $expressionsEditor.height($expressionsEditor.data('beforeFullscreen').height);
                $expressionsEditor.width($expressionsEditor.data('beforeFullscreen').width);
            }
            self.expressionsMirror.refresh();
        },
        /** internal
         * AddMonitorView#_initializeDatePicker()
         *
         * Set up date picker widget.
         **/
        _initializeDatePicker : function() {
            var self = this;

            self.fromDatePicker = $('#fromDatePicker').datetimepicker();
        },

        /** internal
         * AddMonitorView#_saveMonitor(cb)
         * - cb (Function): method to be called after monitor saved.
         *
         * Post new model to the /jobs service route.
         **/
        _saveMonitor : function(cb) {
            var self = this;

            self._setMetrics();

            self.model.save({
                'id'     : null,
                'userId' : self.user.get('id'),
                'appId'  : self.appId
            },
            {
                success : function(model, response, options) { 
                    if ( typeof cb === 'function' ) {
                        cb();
                    }
                    Backbone.Mediator.pub('view:addmonitor:save', {
                        'model'     : model,
                        'message'   : "The monitor '" + model.get('name') + "' was added.",
                        'attention' : 'Monitor Saved!',
                        'status'    : 'success'
                    });

                    self.model = new JobModel();
                },
                error : function(model, xhr, options) {
                    Backbone.Mediator.pub('view:addmonitor:save', {
                        'model'     : model,
                        'message'   : xhr.responseText,
                        'attention' : 'Monitor Save Error!',
                        'status'    : 'error'
                    });
                }
            });
        },
        /** internal
         * AddMonitorView#_closeModal()
         *
         * Call hide on the modal initialized to a saved DOM element.
         **/
        _closeModal : function() {
            var self = this;
            self.modalEl.modal('hide');
        }
    });

    return AddMonitorView;
});
