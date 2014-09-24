/* Not sure how to make javadoc accept this. Will conquer this one another time. -RH
   <style type="text/css">
<!--
#apisupporttable {  margin: 1em auto 1em auto; border-collapse: collapse; border-top: 2px solid black; border-bottom: 2px solid black; }
#apisupporttable th { cell-spacing: 1em; padding: 0.05em 1em 0.05em 1em; border-bottom: 1px solid black; }
#apisupporttable tr.supported:hover { background-color: #CEC; }
#apisupporttable tr.notsupported td{ color: #AAA; }
#apisupporttable tr.notsupported:hover{ background-color: #ECC; }
#apisupporttable td { cell-spacing: 1em; padding: 0.05em 1em 0.05em 1em; }
#apisupporttable th { text-align: center; }
#apisupporttable tr td:first-child + td { text-align: center; }
.depslist { font-size: smaller; margin-left: 4em; }
.depslist li {  margin: 0.2em}
-->
</style>   
*/

/**

        <p>
          This is a <strong>Public Domain</strong> Java library for 
          interacting with the <a href="http://www.pushbullet.com">Pushbullet</a>
          service. It supports both sending and receiving notifications. 
          Thanks to <a href="https://github.com/shakethat/jpushbullet">shakethat at github</a>
          for the insights from his library, which was made for an earlier set of Pushbullet APIs.
        </p>
        


        <p>The <a href="https://docs.pushbullet.com/http/">Pushbullet HTTP API</a> identifies 
        seven capabilities, of which jPushbullet2 supports five:</p>


        <table id="apisupporttable" summary="Supported features">
         <thead><tr><th>Pushbullet API</th><th>Supported in <br>jPushbullet2</th><th>Future Plans</th></tr></thead>
         <tbody>
             <tr class="supported"><td><a href="https://docs.pushbullet.com/v2/pushes">/v2/pushes</a></td><td>Yes</td><td></td></tr>
             <tr class="supported"><td><a href="https://docs.pushbullet.com/v2/devices">/v2/devices</a></td><td>Yes</td><td></td></tr>
             <tr class="notsupported"><td><a href="https://docs.pushbullet.com/v2/contacts">/v2/contacts</a></td><td>No</td><td>Will explore this later</td></tr>
             <tr class="supported"><td><a href="https://docs.pushbullet.com/v2/users/me">/v2/users/me</a></td><td>Yes</td><td></td></tr>
             <tr class="supported"><td><a href="https://docs.pushbullet.com/v2/upload-request">/v2/upload-request</a></td><td>Yes</td><td></td></tr>
             <tr class="notsupported"><td><a href="https://docs.pushbullet.com/v2/oauth2">/v2/oauth2</a></td><td>No</td><td>None</td></tr>
             <tr class="supported"><td><a href="https://docs.pushbullet.com/stream">websocket</a></td><td>Yes</td><td></td></tr>
         </tbody>
         </table>
        
    <p>Begin exploring with the {@link net.iharder.jpushbullet2.PushbulletClient} class.</p>
 *
 * <p>Change Log</p>
  <ul>
   <li>v0.2 - Adding paging capability. Changed Push sorting to be newest first
     in keeping with Pushbullet.com practice.</li>
   <li>v0.1 - Initial Release
  </ul>
 * 
 * 
 * @author Robert Harder
 * @author rob@iharder.net
 * @version 0.2
 */
package net.iharder.jpushbullet2;
