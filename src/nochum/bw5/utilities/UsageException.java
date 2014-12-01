package nochum.bw5.utilities;

class UsageException extends Exception {
		
	/**
	 * 
	 */
	private static final long serialVersionUID = -5665170812412657802L;
	private String usageString="InvalidParameters";
	
	public UsageException(String message, String usageString) {
		super(message);
        this.usageString=usageString;
	}
    
   public String getUsage(){
       return this.usageString;
   }
}
