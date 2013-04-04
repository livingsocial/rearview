define([
    'jquery',
    'underscore',
    'backbone',
    'handlebars',
    'view/base',
    'view/deletemonitor',
    'model/job',
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
    DeleteMonitorView,
    JobModel,
    HighChart
){
    var EditMonitorView = BaseView.extend({

        events : {
            'click .name-save' : 'updateMonitorName'
        },

        subscriptions : {
            'view:alerttimeline:toggle' : 'setAlertTimelineHeightOffset',
            'view:dashboard:init'       : 'exit',
            'view:deletemonitor:delete' : 'deleteMonitor'
        },

        initialize : function(options) {
            var self = this;
            _.bindAll(this);

            self.user    = options.user;
            self.router  = options.router;
            self.templar = options.templar;

            // init delete monitor view
            self.deleteMonitorView = new DeleteMonitorView({
                'el'      : $('.delete-monitor-wrap'),
                'templar' : self.templar
            });

            // use debounce to throttle resize events and set the height when
            // the viewport changes.
            var resize = _.debounce(self._setExpandedViewHeight, 500);

            // Add the event listener
            $(window).resize(resize);

            // subscribe to dashboard change in order to exit current expanded monitor
            // Backbone.Mediator.sub('view:dashboard:init', self.exit, self);
            // Backbone.Mediator.sub('view:deletemonitor:delete', self.deleteMonitor, self);
        },

        render : function(id) {
            var self = this;

            self.monitorId = id;
            self.initMonitor();
        },
        /**
         * EditMonitorView#initMonitor(model)
         * - model (Object): job (monitor) backbone model object
         *
         *
         **/
        initMonitor : function() {
            var self = this;

            // retrieve monitor model & graph data
            self.getMonitor(self.monitorId, function(result) {
                // make sure update has a reference to the instance and model
                // at the time of editing/updating
                self.model = result;
                _.bind(self.updateMonitor, self, self.model);

                self.getGraphData(self.monitorId, function(result) {

                    self._initErrorsList(function(errors) {
                        self.templar.render({
                            path : 'expandedmonitor',
                            el   : self.$el,
                            data : {
                                'monitor' : self.model.toJSON(),
                                'errors'  : errors,
                                'alerts'  : errors,
                                'output'  : self.model.get('output')
                            }
                        });

                        self.$monitor = self.$el.find('.expanded-monitor');
                        //edit-monitor-wrap container element
                        self.$el.html( self.$monitor );
                        // add events to the view buttons in the alert window
                        self._setAlertsHistory();
                        // setup the graph area
                        self.chart = self.initGraph( self.$monitor.find('.graph')[0]);
                        // set error state
                        self._setErrorState();

                        self.updateGraph(self.model, function(output) {
                            self._initializeCodeMirror();
                            self._initializeDatePicker('.input-from-date');
                            self._setErrorDropDown();
                            self._setScheduleData();
                            self._setEditNameField();
                            self.$el.find('.save-changes').click({'model' : self.model}, self.updateMonitor);
                            self.$el.find('.cancel-changes').click(function() {
                                self.exit();
                            });
                            self.$el.find('.test-in-graph button').click({
                                'model'  : new JobModel(),
                                'output' : output
                            }, self.testMonitor, self);

                            // finally open up the edit monitor widget
                            self.open();
                        }, self.formattedGraphData);

                        // inline help
                        self.setHelp();

                        // dynamically set the heights to maximum screen utilization
                        self._setExpandedViewHeight();
                    }, self.model);

                });
            });

        },

        getGraphData : function(monitorId, cb) {
            var self = this;
            $.ajax({
                url   : '/jobs/' + monitorId + '/data',
                success : function(result) {
                    if ( result.graph_data ) {
                        self.formattedGraphData = self.formatGraphData( result.graph_data );
                        if ( !_.isUndefined(cb) ) {
                            cb(result);
                        }
                    }
                },
                error : function(result) {
                    if ( !_.isUndefined(cb) ) {
                        cb(result);
                    }
                }
            });
        },

        getMonitor : function(monitorId, cb) {
            var self = this;

            self.model = new JobModel({
                id : monitorId
            });

            self.model.fetch({
                success : function(result) {
                    if ( !_.isUndefined(cb) ) {
                        cb(result);
                    }
                }
            });
        },

        getErrorStatus : function(status) {
            var self  = this,
                error = false;

            switch ( status ) {
                case 'error' :
                    error = true;
                    break;
                case 'failed' :
                    error = true;
                    break;
                case 'graphite_error' :
                    error = true;
                    break;
                case 'graphite_metric_error' :
                    error = true;
                    break;
            }

            return error;
        },
        setHelp : function() {
            var self     = this,
                $content = '';

            $.ajax({
                url     : '/help/quick.html',
                async   : false,
                success : function( response ) {
                    $content = response;
                }
            });

            var $help = self.$el.find('.help');

            $help.tooltip({
                trigger   : 'click',
                html      : true,
                placement : 'right',
                delay     : { show : 100, hide : 200 },
                title     : $content
            });
        },
        /**
         * EditMonitorView#open()
         *
         *
         **/
        open : function() {
            var self = this;
            self.$el.addClass('active');

            //publish for other views can update
            Backbone.Mediator.pub('view:editmonitor:open', {
                'nav' : {
                    'back' : true
                }
            });

            // set route for hotlinking purposes
            self.router.navigate('dash/' + self.model.get('appId') + '/expand/' + self.model.get('id'));
        },
        /**
         * EditMonitorView#exit()
         *
         *
         **/
        exit : function(monitor) {
            var self    = this,
                monitor = ( monitor ) ? monitor : null;

            self.$el.removeClass('active');

            // set route for hotlinking purposes
            if(self.model) {
                self.router.navigate('dash/' + self.model.get('appId'));
            }

            // fire event that editmonitor is exiting
            Backbone.Mediator.pub('view:editmonitor:exit', {
                status : 'delete'
            });
        },
        /**
         * EditMonitorView#updateGraph(model, cb)
         * - model (Object): job (monitor) backbone model object
         * - cb (Function): callback function
         *
         **/
        updateGraph : function(model, cb, graphData) {
            var self = this;

            if(graphData) {
                self.renderGraphData(self.chart, graphData);

                if(typeof cb === 'function') {
                    cb();
                }
            } else {
                $.ajax({
                    url     : '/monitor',
                    type    : 'POST',
                    data    : JSON.stringify(model.toJSON()),
                    success : function(result) {
                        if ( result.graph_data ) {
                            var formattedGraphData = self.formatGraphData( result.graph_data );
                            self.renderGraphData(self.chart, formattedGraphData);
                            self.$el.find('textarea.output-view').val(result.output);
                        }

                        if(typeof cb === 'function') {
                            cb(result.output);
                        }
                    }
                });
            }
        },
        /**
         * EditMonitorView#testMonitor(e)
         *
         *
         **/
        testMonitor : function(e) {
            var self        = this,
                monitor     = e.data.model,
                output      = e.data.output,
                $testBtn    = $(e.target);

            // set button state
            $testBtn.button('loading');

            monitor = self._setMetrics(monitor);
            monitor = self._setSchedule(monitor);

            // update to the output view for testing
            self.$el.find('textarea.output-view').val(output);
            self.$el.find('.output-tab').tab('show');

            self.updateGraph(monitor, function() {
                $testBtn.button('reset');
            });
        },
        /**
         * EditMonitorView#viewErrorInGraph(e)
         *
         *
         **/
        viewErrorInGraph : function(e) {
            var self        = this,
                monitor     = e.data.model,
                output      = e.data.output,
                toDate      = e.data.toDate;

            monitor.set('toDate', toDate);

            // viewError in graph gets the from
            // toDate field not from the html field
            monitor = self._setMetrics(monitor, true);
            monitor = self._setSchedule(monitor);

            // otherwise update to the output view for testing
            self.$el.find('textarea.output-view').val(output);
            self.$el.find('.output-tab').tab('show');

            self.updateGraph(monitor);
        },
        /**
         * EditMonitorView#updateMonitor(e)
         *
         *
         **/
        updateMonitor : function(e) {
            var self        = this,
                monitor     = e.data.model,
                test        = e.data.test,
                toDate      = e.data.toDate,
                output      = e.data.output;

            monitor = self._setMetrics(monitor);
            monitor = self._setSchedule(monitor);

            monitor.save(null, {
                success : function(model, response, options) {
                    Backbone.Mediator.pub('view:editmonitor:save', {
                        'model'     : self.model,
                        'message'   : "The monitor '" + model.get('name') + "' was saved.",
                        'attention' : 'Monitor Saved!',
                        'status'    : 'success'
                    });

                    // quit out of the edit monitor view
                    self.exit(monitor);
                    self.updateGraph(monitor);
                },
                error : function(model, xhr, options) {
                    Backbone.Mediator.pub('view:editmonitor:save', {
                        'model'     : self.model,
                        'message'   : "The monitor '" + model.get('name') + "' caused an error on saving, please try again.",
                        'attention' : 'Monitor Saved Error!',
                        'status'    : 'error'
                    });
                }
            });
        },
        /**
         * EditMonitorView#deleteMonitor(e)
         *
         *
         **/
        deleteMonitor : function() {
            var self    = this;

            self.model.destroy({
                success : function(model, response) {
                    Backbone.Mediator.pub('view:editmonitor:delete', {
                        'model'     : self.model,
                        'message'   : "The monitor '" + model.get('name') + "' was removed from the current application.",
                        'attention' : 'Monitor Deleted!'
                    });
                },
                error : function(model, response) {
                    Backbone.Mediator.pub('view:editmonitor:delete', {
                        'model'     : self.model,
                        'message'   : "The monitor '" + model.get('name') + "' produced an error during the process of deletion.",
                        'attention' : 'Monitor Delete Error!',
                        'status'    : 'error'
                    });
                }
            });

            self.exit();
        },
        /**
         * EditMonitorView#updateMonitorName()
         */
        updateMonitorName : function() {
            var self = this;

            var $nameField   = self.$el.find('.name-field'),
                previousName = $nameField.prev().html();

            self.model.set({
                'name' : $nameField.find('input').val()
            });

            $nameField.prev().html( self.model.get('name') );
            $nameField.hide();
            $nameField.prev().show();
        },
        /**
         * EditMonitorView#resize()
         */
        resize : function() {
            var self = this;
            return _.debounce(self._setExpandedViewHeight, 500);
        },
        /**
         * EditMonitorView#destructor()
         */
        destructor : function() {
            var self          = this,
                $prevSibling  = self.$el.prev();

            // clean up nested views
            self.deleteMonitorView.destructor();

            // unsubscribe from mediator channels
            self.destroySubscriptions();

            self.remove();
            self.unbind();
            self.off();

            $prevSibling.after("<div class='edit-monitor-wrap clearfix'>");
        },
        setAlertTimelineHeightOffset : function(heightOffset) {
            var self = this;

            self.alertTimelineOffset = heightOffset;
            self._setExpandedViewHeight();
        },

        /**
         *
         */
        _setExpandedViewHeight : function() {
            var self         = this,
                windowOffSet = ( this.alertTimelineOffset ) ? ( 580 + this.alertTimelineOffset ) : 580,
                alertsOffset = 6,
                outputOffset = 4;

            self.$el.find('.output-view').css({
                'height' : ( $(window).height() - windowOffSet - outputOffset ) + 'px'
            });

            self.$el.find('.alerts-history ul').css({
                'height' : ( $(window).height() - windowOffSet - alertsOffset ) + 'px'
            });

            self.$el.find('.graph').css({
                'height' : ( $(window).height() - windowOffSet ) + 'px'
            });

            // set chart height dynamically
            var chartHeight = ( ($(window).height() - windowOffSet) > 150 ) ? $(window).height() - windowOffSet : 150;
            if (self.chart && chartHeight > 150) {
                self.chart.setSize(null, chartHeight);
            }
        },
        _setErrorState : function() {
            var self        = this,
                errorStatus = self.getErrorStatus(self.model.get('status'));

            if( errorStatus ) {
                self.$monitor.addClass('red');
            } else {
                self.$monitor.removeClass('red');
            }
        },
        /** internal
         * EditMonitorView#_initErrorsList(cb)
         *
         *
         **/
        _initErrorsList : function(cb) {
            var self      = this,
                monitorId = self.model.get('id');

            $.get('/jobs/' + monitorId + '/errors', function(data) {
                data = _.map(data, function(error) {
                    return {
                        label   : new XDate(error.date, true).toString("MM/dd/yyyy HH:mm"),
                        value   : error.date,
                        message : error.message,
                        id      : error.id
                    }
                });

                cb(data);
            });
        },
        /** internal
         * EditMonitorView#_setErrorDropDown()
         **/
        _setErrorDropDown : function() {
            var self = this;

            self.$selectPreviousError = $('#selectPreviousError');
            self.$selectPreviousError.change(function() {
                var selectedErrorDateTime = $(this).find('option:selected').html();
                self.fromDatePicker.datetimepicker('setDate', selectedErrorDateTime);
            });
        },
        /** internal
         * EditMonitorView#_setEditNameField()
         *
         *
         **/
        _setEditNameField : function() {
            var self       = this,
                $nameField = self.$el.find('.name-field');

            $nameField.prev().click(function() {
                $nameField.prev().hide();
                $nameField.show();
                $nameField.find('input').focus();
            });
        },
        /** internal
         * EditMonitorView#_initializeCodeMirror()
         *
         *
         **/
        _initializeCodeMirror : function() {
            var self          = this,
                $expressions  = self.$el.find('#inputExpressions')[0],
                $metrics      = self.$el.find('#inputMetrics')[0];

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
                        return self._toggleFullscreen('.expanded-monitor .expressions .CodeMirror', self.expressionsMirror);
                    }
                }
            });

            self.metricsMirror = CodeMirror.fromTextArea( $metrics, {
                value        : '',
                lineNumbers  : true,
                lineWrapping : true,
                mode         : 'ruby',
                theme        : 'ambiance',
                onKeyEvent   : function(i, e) {
                    if (( e.keyCode == 70 && e.ctrlKey ) && e.type == 'keydown') {
                        e.stop();
                        return self._toggleFullscreen('.expanded-monitor .metrics .CodeMirror', self.metricsMirror);
                    }
                }
            });
        },
        /** internal
         * EditMonitorView#_initializeDatePicker()
         *
         *
         **/
        _initializeDatePicker : function(selector) {
            var self = this;
            self.fromDatePicker = self.$el.find(selector).datetimepicker();
        },
        /** internal
         * EditMonitorView#_setScheduleData(model)
         *
         *
         **/
        _setScheduleData : function() {
            var self     = this,
                cronExpr = self.model.get('cronExpr').split(' '); // space delimited cron job expression

            self.$el.find('#inputSeconds').val( cronExpr[0] );
            self.$el.find('#inputMinutes').val( cronExpr[1] );
            self.$el.find('#inputHours').val( cronExpr[2] );
            self.$el.find('#inputDays').val( cronExpr[3] );

            var month   = cronExpr[4].split(','),
                weekday = cronExpr[5].split(',');

            self._setButtonGroup( self.$el.find('.day-picker button'), weekday );
            self._setButtonGroup( self.$el.find('.month-picker button'), month );
        },
        /** internal
         * EditMonitorView#_setButtonGroup(collection, dataList)
         *
         *
         **/
        _setButtonGroup : function(collection, dataList) {
            collection.each(function(index, day) {
                day = $(day);

                var dataValue = _.indexOf(dataList, day.attr('data-value'))

                if ( dataValue > -1 ) {
                    day.addClass('active');
                }
            });
        },
        /** internal
         * EditMonitorView#_setMetrics(model)
         *
         * Set metrics data to the job model.
         **/
        _setMetrics : function( model, errorView ) {
            var self = this;

            model.set({
                'userId'      : self.user.get('id'),
                'monitorExpr' : self.expressionsMirror.getValue(),
                'metrics'     : self.metricsMirror.getValue().split('\n'),
                'minutes'     : parseInt(self.$el.find('.input-minutes-back').val()),
                'toDate'      : ( errorView ) ? model.get('toDate') : self.$el.find('.input-from-date').val()
            });

            return model;
        },
        /** internal
         * EditMonitorView#_setSchedule(model)
         *
         * Set scheduling data to the job model.
         **/
        _setSchedule : function( model ) {
            var self = this,
                monitorName = self.$el.find('.monitor-name')[0]
                            ? self.$el.find('.monitor-name').val()
                            : model.get('name');
            // grab form data
            model.set({
                'userId'        : self.user.get('id'),
                'name'          : monitorName,
                'description'   : self.$el.find('#description').val(),
                'alertKeys'     : self.parseAlertKeys( self.$el.find('.pager-duty textarea').val() ),
                'cronExpr'      : self._createCronExpr()
            });

            return model;
        },

        _setAlertsHistory : function() {
            var self = this;
            self.$monitor.find('.alerts-history ul').delegate( "li button", "click", function(e) {
                self.viewErrorInGraph({
                    'data' : {
                        'model' : new JobModel(),
                        'test'  : true,
                        'toDate': $(e.target).attr('data-date')
                    }
                });

                // copy datetime to from field
                self.fromDatePicker.datetimepicker('setDate', $(e.target).attr('data-date'));
            });
        }
    });

    return EditMonitorView;
});
