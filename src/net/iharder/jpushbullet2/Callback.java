package net.iharder.jpushbullet2;

/**
 * Used for callbacks when doing async pushes. The single method
 * {@link #completed} is passed the string result from the HTTP
 * connection (probably in JSON form) and any exception that
 * might have been thrown.
 * 
* 
* <table border="1">
* <caption>Possible argument values</caption>
* <thead><tr><th></th><th>String arg</th><th>{@link PushbulletException} arg</th></tr></thead>
* <tbody>
* <tr><td>Successful</td><td>HTTP response</td><td>null</td></tr>
* <tr><td>Failure</td><td>null</td><td>The related exception</td></tr>
* </tbody>
* </table>
 * 
 * @author Robert.Harder
 */
public interface Callback<T> {
    
    /**
     * Called when an asynchronous push is completed.
     * @param result The result of whatever took so long to complete
     * or null if there was an exception
     * @param ex The {@link PushbulletException} thrown if there was
     * trouble or null otherwise.
     */
    public abstract void completed( T result, PushbulletException ex );
}
