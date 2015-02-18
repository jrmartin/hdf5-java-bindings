/**
 * Tests create file class 
 * 
 * @author jrmartin
 *
 */
public class TestCreateFile {
	public static void main(String[] args){
		CreateFileJunit f = new CreateFileJunit();
		try {
			f.createHDF5File();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
