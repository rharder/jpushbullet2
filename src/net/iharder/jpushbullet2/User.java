
package net.iharder.jpushbullet2;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a user as decoded from the json data from Pushbullet.
 * 
 * @author rob
 */
public class User {
    protected String iden;
    protected double created;
    protected double modified;    
    protected String email;
    protected String email_normalized;
    protected String name;
    protected String image_url;
    protected Object google_userinfo;
    protected Object preferences;

    public String getIden() {
        return iden;
    }

    public void setIden(String iden) {
        this.iden = iden;
    }


    public double getCreated() {
        return created;
    }

    public void setCreated(double created) {
        this.created = created;
    }
  
    public double getModified() {
        return modified;
    }

    public void setModified(double modified) {
        this.modified = modified;
    }


    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }


    public String getEmail_normalized() {
        return email_normalized;
    }

    public void setEmail_normalized(String email_normalized) {
        this.email_normalized = email_normalized;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public String getImage_url() {
        return image_url;
    }

    public void setImage_url(String image_url) {
        this.image_url = image_url;
    }

    /*public Object getGoogle_userinfo() {
        return google_userinfo;
    }

    public void setGoogle_userinfo(Object google_userinfo) {
        this.google_userinfo = google_userinfo;
    }

    public Object getPreferences() {
        return preferences;
    }

    public void setPreferences(Object preferences) {
        this.preferences = preferences;
    }
*/
    
    @Override
    public String toString(){
        StringBuilder s = new StringBuilder();
        s.append( "{").append(this.getClass().getSimpleName());
        for( Field f : this.getClass().getDeclaredFields() ){
            s.append(", ");
            s.append( f.getName() ).append("=");
            try {
                s.append( f.get(this) );
            } catch (Exception ex) {
                //Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
            } 
        }
        s.append("}");
        return s.toString();
    }
    
    
    
}
