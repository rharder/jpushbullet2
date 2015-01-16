
package net.iharder.jpushbullet2;

import java.lang.reflect.Field;

/**
 * Represents a user as decoded from the json data from Pushbullet.
 * It is created by introspection and thus doesn't have any full
 * constructors. It is immutable and so has no setter methods.
 * 
 * 
 * 
 * @author rob
 */
public final class User implements Comparable<User>{
	
    private String iden;
    private double created;
    private double modified;    
    private String email;
    private String email_normalized;
    private String name;
    private String image_url;
    private Object google_userinfo;
    private Object preferences;


    public String getIden() {
        return iden;
    }

    public void setIden(String iden) {
        this.iden = iden;
    }


    public double getCreated() {
        return created;
    }
  
    public double getModified() {
        return modified;
    }

    public String getEmail() {
        return email;
    }

    public String getEmail_normalized() {
        return email_normalized;
    }

    public String getName() {
        return name;
    }

    public String getImage_url() {
        return image_url;
    }

    /**
     * Sorts according to <tt>name</tt> field.
     */
    @Override
    public int compareTo(User o) {
        return this.name == null ? -1 : this.name.compareTo(o.name);
    }

    /**
     * Two users will be considered equal if their <tt>iden</tt>
     * fields are equal.
     *
     * @param obj The other user to compare
     * @return true if the two users have the same <tt>iden</tt>
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        if (obj.getClass() != getClass()) {
            return false;
        }

        if( this.iden == null ){
            return false;
        }
        
        User rhs = (User) obj;
        return this.iden.equals(rhs.iden);
    }

    @Override
    public int hashCode() {
        return this.iden == null ? super.hashCode() : this.iden.hashCode();
    }
    
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
            } 
        }
        s.append("}");
        return s.toString();
    }
    
    
    
}
