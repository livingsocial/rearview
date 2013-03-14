Rearview API
============

This document outlines the various URI's exposed as the RESTful API for rearview.

General
-------

<table>
    <thead>
        <tr>
            <th class="resource">Resource</th>
            <th class="description">Description</th>
        </tr>
     </thead>
     <tbody>
         <tr>
             <td class="resource">GET /</td>
             <td class="description">Returns the default template, currently index.scala.html.  The user must first be authenticated via OpenID.</td>
         </tr>
         <tr>
             <td class="resource">GET /login</td>
             <td class="description">If a user is not authenticated, handles redirecting to Google Apps login.  Google Apps login will subsequently redirect back to /loginCallback for verification. The user is redirected to /index if there is a valid session.</td>
         </tr>
         <tr>
             <td class="resource">GET /loginCallback</td>
             <td class="description">A callback URI used by OpenID once the authentication sequence has completed on the server.</td>
         </tr>
         <tr>
             <td class="resource">GET /user</td>
             <td class="description">Returns a JSON representation of the currently logged in user.  A user is of the form:<br>
             <code><pre>
{
  "id" : 26,
  "email" : "jeff.simpson@hungrymachine.com",
  "firstName" : "Jeff",
  "lastName" : "Simpson",
  "lastLogin" : 1340279894000
}
             </pre></code>
             </td>
         </tr>
     </tbody>
</table>

Monitor
-------
<table>
    <thead>
        <tr>
            <th class="resource">Resource</th>
            <th class="description">Description</th>
        </tr>
    </thead>
    <tbody>
        <tr>
             <td class="resource">POST /monitor</td>
             <td class="description">Accepts JSON representing a Job's monitor code and settings.  Runs the monitor through the JRuby interpretter against the metrics defined.  A monitor defined as follows:<br>
             The response from a monitor test run has the following form:
             <code><pre>
{
  "status" : "success",
  "output" : "",
  "graph_data" : {
    "successful logins":[
      [1349282860,5.0],
      [1349282870,6.0],
      [1349282880,7.0],
      [1349282890,3.0],
      [1349282900,1.0],
      [1349282910,5.0]]
    }
}
             </pre></code>
             <i>status</i> is one of: success, failed, error, graphite_error<br>
             <i>output</i> is the raw output returned from the monitor such as puts statements, Ruby errors, etc.  It's a combine stream from stdout and stderr.<br>
             <i>graphite_data</i> is a hash with the metric name and an array of metric data.  Each entry in the data is a tuple of timestamp/value.
             </td>
         </tr>
    </tbody>
</table>

Graphite
--------
<table>
    <thead>
        <th class="resource">Resource</th>
        <th class="description">Description</th>
    </thead>
    <tr>
        <td class="resource">GET /graphite/*path</td>
        <td class="description">Proxies requests to Graphite via the authenticated session.  The monitors do not interact directly with the Graphite API, but instead a URI is built behind the scenes based on the metric parameters and time period for the monitor.  The URI can be accessed directly from the authenticated browser session for rendering adhoc Graphite data, etc.  For a full description of the Graphite API consult, <a href=https://graphite.readthedocs.org/en/0.9.10/render_api.html">https://graphite.readthedocs.org/en/0.9.10/render_api.html</a></td>
    </tr>
</table>

Applications (crud)
-----------

Applications are containers for Jobs.  The following section defines the CRUD operations supported.  An Application has the following structure:

    {
        "id" : <number>,              // An optional, non-negative integer.
        "userId" : <number>,          // An optional, non-negative integer.
        "name" : <string>             // The name of the monitor as displayed in the UI
     }

<table>
    <thead>
        <tr>
            <th class="resource">Resource</th>
            <th class="description">Description</td>
        </tr>
     </thead>
     <tbody>
         <tr>
             <td class="resource">GET /applications</td>
             <td class="description">Returns an array of Applications</td>
         </tr>
         <tr>
             <td class="resource">GET /applications/:id</td>
             <td class="description">Returns an Application specified by <i>id</i></td>
         </tr>
         <tr>
             <td class="resource">GET /applications/:id/jobs</td>
             <td class="description">Returns all the Jobs for the given <i>id</i></td>
         </tr>
         <tr>
             <td class="resource">POST /applications</td>
             <td class="description">Creates an Application.</td>
         </tr>
         <tr>
             <td class="resource">PUT /applications/:id</td>
             <td class="description">Updates the Application for the given <i>id</i></td>
         </tr>
         <tr>
             <td class="resource">DELETE /applications/:id</td>
             <td class="description">Deletes an Application by <i>id</i></td>
         </tr>
     </tbody>
</table>


Jobs (crud)
-----------

Jobs are the central data type in Rearview.  The following API calls support some basic CRUD operations on Jobs.  A Job has the following structure:

    {
        "id"       : <number>,      // An optional, non-negative integer.
        "userId"   : <number>,      // An optional, non-negative integer.
        "version"  : <number>,      // An optional, non-negative integer. Represents a revision from saving the Job
        "jobType"  : "monitor",     // Always "monitor" for now
        "name"     : <string>,      // The name of the monitor as displayed in the UI
        "cronExpr" : "0 * * * * ?", // A Cron expression created from the UI elements corresponding to each part in Cron.
        "minutes"  : 10,            // The number of minutes back.  Gets converted into the Graphite time format for an API call.
        "metrics"  : [
           "stats_counts.foo.bar", // The metrics combined by Rearview to create and assembled into the Graphite API format.
           "stats_counts.foo.baz"
        ],
        "monitorExpr" : "puts \"Hello, world!\""  // This is the actual Ruby code which is evaluated
        "toDate"      : <string>,                 // optional date to go back n-minutes from
        "active"      : <boolean>,
        "status"      : null,
        "lastRun"     : null,
        "nextRun"     : null,
        "alertKeys"   : [""],
     }

<table>
    <thead>
        <tr>
            <th class="resource">Resource</th>
            <th class="description">Description</td>
        </tr>
     </thead>
     <tbody>
         <tr>
             <td class="resource">GET /jobs</td>
             <td class="description">Returns an array of Jobs</td>
         </tr>
         <tr>
             <td class="resource">GET /jobs/:id</td>
             <td class="description">Returns a Job specified by <i>id</i></td>
         </tr>
         <tr>
             <td class="resource">GET /jobs/:id/data</td>
             <td class="description">Returns the data for the last run of the Job for the given <i>id</i></td>
         </tr>
         <tr>
             <td class="resource">GET /jobs/:id/errors</td>
             <td class="description">Returns the errors for the Job by <i>id</i></td>
         </tr>
         <tr>
             <td class="resource">POST /jobs</td>
             <td class="description">Creates a new Job.</td>
         </tr>
         <tr>
             <td class="resource">PUT /jobs/:id</td>
             <td class="description">Updates a Job for the given <i>id</i></td>
         </tr>
         <tr>
             <td class="resource">DELETE /jobs/:id</td>
             <td class="description">Deletes the Job by <i>id</i></td>
         </tr>
     </tbody>
</table>


Utility
-------
<table>
    <thead>
        <tr>
            <th class="resource">Resource</th>
            <th class="description">Description</td>
        </tr>
     </thead>
     <tbody>
         <tr>
             <td class="resource">GET /currentTime</td>
             <td class="description">Returns the servers current time as an epoch (in milliseconds).</td>
         </tr>
     </tbody>
</table>


Assets
------
<table>
    <thead>
        <tr>
            <th class="resource">Resource</th>
            <th class="description">Description</td>
        </tr>
     </thead>
     <tbody>
         <tr>
             <td class="resource">GET /assets/*file</td>
             <td class="description"><i>*file</i> represents a relative path which gets mapped to the physical localtion <i>public/*file</i>.</td>
         </tr>
     </tbody>
</table>
