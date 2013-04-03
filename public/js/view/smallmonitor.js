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
    var SmallMonitorView = BaseView.extend({

        el : '.small-monitor',

        events : {
            'click .header-name'   : 'editMonitor',
            'click .settings .btn' : 'monitorSettings',
            'click .save'          : 'monitorSettings'
        },

        initialize : function(options) {
            var self = this;
            self.templar = options.templar;
            _.bindAll(self);

            self.addHelpers();
            self.intervalLength = ( options.intervalLength ) ? options.intervalLength : 60; // seconds
            Backbone.Mediator.sub('controller:dashboard:init', self.expandMonitor, self);

            // use debounce to throttle resize events and set the height when
            // the viewport changes.
            var resize = _.debounce(self.monitorTitle, 500);

            // Add the event listener
            $(window).resize(resize);
        },

        render : function() {
            var self = this;

            self.templar.render({
                path   : 'smallmonitor',
                el     : self.$el,
                append : true,
                data   : {
                    'monitor' : self.model.toJSON()
                }
            });

            self.initMonitor();

            return self;
        },
        /**
         * SmallMonitorView#initMonitor()
         *
         * Set up the small monitor with job (monitor) model, append to
         * monitor list area, then finally get monitor data to create graph.
         **/
        initMonitor : function() {
            var self = this;
            self.$monitor = self.$el.find('#smallMonitor' + self.model.get('id'))
            self.$graph   = self.$monitor.find('.graph')[0];
            self.$monitor.find('h1').tooltip();
            
            self._applyEvents(self.$graph);

            self.chart = self.initGraph( self.$graph );
            // update the graph on load
            self.updateGraph();
            // update the graph every interval
            self.interval = setInterval(function() {
                self.updateGraph(true);
            }, self.intervalLength * 1000);
        },
        updateGraph : function(period) {
            var self      = this,
                runUpdate = false;

            if ( !period ) {
                runUpdate = true;
            } else if ( period && self.model.get('active') ) {
                runUpdate = true;
            }

            if (runUpdate) {
                // should be in an error state till proven otherwise
                self.errorState = true;

                self._showOverlay(self.$graph, 'Loading...', 'small-monitor-overlay');

                $.ajax({
                    url   : '/jobs/' + self.model.get('id') + '/data',
                    async : true,
                    success : function(result) {
                        if (result.status === 'error') {
                            self._hideOverlay();
                            if(_.isEmpty(result.graph_data)) {
                                self._showOverlay(self.$graph, 'Monitor Error - No Data', 'small-monitor-error-overlay');
                            } else {
                                self._showOverlay(self.$graph, 'Monitor Error', 'small-monitor-error-overlay');
                            }
                            self._setErrorState();
                        } else if (result.status === 'failed') {
                            self._hideOverlay();
                            self.formattedGraphData = self.formatGraphData( result.graph_data );
                            self.renderGraphData(self.chart, self.formattedGraphData);
                            self._setErrorState();
                            // set the output data so when we pass the model to the expanded view
                            // we already have it
                            self.model.set('output', result.output);
                        } else if (result.status === 'graphite_error') {
                            self._hideOverlay();
                            self._showOverlay(self.$graph, 'Graphite Error', 'small-monitor-error-overlay');
                            self._setErrorState();
                        } else if (result.status === 'graphite_metric_error') {
                            self._hideOverlay();
                            self._showOverlay(self.$graph, 'Graphite Metrics Error', 'small-monitor-error-overlay');
                            self._setErrorState();
                        } else if ( result.graph_data ) {
                            self._hideOverlay();
                            self.errorState = false;
                            self.formattedGraphData = self.formatGraphData( result.graph_data );
                            self.renderGraphData(self.chart, self.formattedGraphData);
                            // set the output data so when we pass the model to the expanded view
                            // we already have it
                            self.model.set('output', result.output);
                            self._setErrorState(true); // clear out error states if any on previous update
                        } else {
                            self._hideOverlay();
                            self._showOverlay(self.$graph, 'Unexpected Error', 'small-monitor-error-overlay');
                            self._setErrorState();
                        }
                    },
                    error : function() {
                        self._hideOverlay();
                        self._showOverlay(self.$graph, 'Waiting For Next Run', 'small-monitor-error-overlay');
                    }
                });
                
            }
        },
        /**
         * SmallMonitorView#deactivateMonitor(e)
         * - e (Object): event object
         *
         * After checking if the inactive button is not already set,
         * simply setting the model to reflect the active state and saving to db.
         **/
        deactivateMonitor : function(e) {
            var self = this;

            if( !$(e.target).hasClass('active') ) {
                this.model.save({
                    'active' : false
                },
                {
                    error : function(model, xhr, options) {
                        Backbone.Mediator.pub('view:smallmonitor:save', {
                            'model'     : self.model,
                            'message'   : "The monitor '" + model.get('name') + "' caused an error on deactivation, please check your monitor code.",
                            'attention' : 'Monitor Deactivate Error!',
                            'status'    : 'error'
                        });
                    }
                });
            }
        },
        /**
         * SmallMonitorView#activateMonitor(e)
         * - e (Object): event object
         *
         * After checking if the active button is not already set,
         * simply setting the model to reflect the active state and saving to db.
         **/
        activateMonitor : function(e) {
            var self = this;

            if( !$(e.target).hasClass('active') ) {
                this.model.save({
                    'active' : true
                },
                {
                    error : function(model, xhr, options) {
                        Backbone.Mediator.pub('view:smallmonitor:save', {
                            'model'     : self.model,
                            'message'   : "The monitor '" + model.get('name') + "' caused an error on activation, please check your monitor code.",
                            'attention' : 'Monitor Activate Error!',
                            'status'    : 'error'
                        });
                    }
                });
            }
        },
        expandMonitor : function(data) {
            var self = this;

            if( data.monitorId == self.model.get('id') ) {
                self.editMonitor();
            }
        },
        /**
         * SmallMonitorView#editMonitor(e)
         * - e (Object): event object
         *
         * Publish an view:edit channel publish event and pass the model
         * for the EditMonitorView for example.
         **/
        editMonitor : function() {
            var self = this;

            Backbone.Mediator.pub('view:smallmonitor:edit', self.model.get('id'), self);
        },
        monitorSettings : function(e) {
            self = this;
            self.$monitor.toggleClass('flipped');
        },

        monitorTitle : function() {
            var self         = this,
                buttonOffset = 110,
                monitorWidth = self.$monitor.width();

            self.$header    = self.$monitor.find('.header-name');
            self.$titleText = self.$monitor.find('.header-name p');

            // calculate maximum fluid width minus the current header control buttons
            self.$header.css('width', monitorWidth - buttonOffset);

            if ( self.$titleText.width() > self.$header.width() ) {
                self.$header.addClass('truncated');
            } else {
                self.$header.removeClass('truncated');
            }
        },

        _setErrorState : function(state) {
            var self = this;
            if(!state) {
                self.$el.children(":first").addClass('red');
            } else {
                self.$el.children(":first").removeClass('red');
            }
        },
        _applyEvents : function() {
            var self = this;
            self.$el.find('.monitor-inactive').click(self.deactivateMonitor);
            self.$el.find('.monitor-active').click(self.activateMonitor);
        },

        nextRun : function() {
            var self = this;
            self._hideOverlay();
            self._showOverlay(self.$graph, 'Waiting For Next Run', 'small-monitor-error-overlay');
        },
        
        destructor : function() {
            var self = this;

            // go ahead and clear out the graph updating interval
            clearInterval(self.interval);

            self.model.unbind();
            self.unbind();
            self.off();
            self.undelegateEvents();

            Backbone.Mediator.unsubscribe('view:smallmonitor:edit', self.editMonitor);
            Backbone.Mediator.unsubscribe('controller:dashboard:init', self.expandMonitor, self);

            self.remove();
            self.$el.remove();
        }

    });

    return SmallMonitorView;
});