package com.fullteaching.backend.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Iterator;

import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.http.fileupload.IOUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/*//ONLY ON PRODUCTION
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import org.springframework.beans.factory.annotation.Value;
//ONLY ON PRODUCTION*/

import com.fullteaching.backend.course.Course;
import com.fullteaching.backend.course.CourseRepository;
import com.fullteaching.backend.filegroup.FileGroup;
import com.fullteaching.backend.filegroup.FileGroupRepository;
import com.fullteaching.backend.user.User;
import com.fullteaching.backend.user.UserRepository;
import com.fullteaching.backend.user.UserComponent;

@RestController
@RequestMapping("/load-files")
public class FileController {
	
	@Autowired
	private FileGroupRepository fileGroupRepository;
	
	@Autowired
	private CourseRepository courseRepository;
	
	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private UserComponent user;
	
	/*//ONLY ON PRODUCTION
	@Autowired
	private AmazonS3 amazonS3;
    @Value("${aws_namecard_bucket}")
    private String bucketAWS;
    //ONLY ON PRODUCTION*/
    
	private static final Path FILES_FOLDER = Paths.get(System.getProperty("user.dir"), "files");
	private static final Path PICTURES_FOLDER = Paths.get(System.getProperty("user.dir"), "pictures");

	@RequestMapping(value = "/upload/course/{courseId}/file-group/{fileGroupId}", method = RequestMethod.POST)
	public ResponseEntity<FileGroup> handleFileUpload(
			MultipartHttpServletRequest request,
			@PathVariable(value="courseId") String courseId,
			@PathVariable(value="fileGroupId") String fileGroupId
		) throws IOException {
		
		long id_course = -1;
		long id_fileGroup = -1;
		try {
			id_course = Long.parseLong(courseId);
			id_fileGroup = Long.parseLong(fileGroupId);
		} catch(NumberFormatException e){
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		Course c = courseRepository.findOne(id_course);
		
		checkAuthorization(c, c.getTeacher());
		
		FileGroup fg = null;
		Iterator<String> i = request.getFileNames();
		while (i.hasNext()) {
			String name = i.next();
			System.out.println("X - " + name);
			MultipartFile file = request.getFile(name);
			
			System.out.println("FILE: " + file.getOriginalFilename());
		
			if (file.isEmpty()) {
				
				System.out.println("EXCEPTION!");
				
				throw new RuntimeException("The file is empty");
			}
	
			if (!Files.exists(FILES_FOLDER)) {
				
				System.out.println("PATH CREATED");
				
				Files.createDirectories(FILES_FOLDER);
			}
	
			String fileName = file.getOriginalFilename();
			File uploadedFile = new File(FILES_FOLDER.toFile(), fileName);
			file.transferTo(uploadedFile);
			
			//ONLY ON DEVELOPMENT
			fg = fileGroupRepository.findOne(id_fileGroup);
			fg.getFiles().add(new com.fullteaching.backend.file.File(1, file.getOriginalFilename(), uploadedFile.getPath()));
			fg.updateFileIndexOrder();
			//ONLY ON DEVELOPMENT
			
			/*//ONLY ON PRODUCTION
			try {
				this.productionFileSaver(fileName, "files", uploadedFile);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			fg = fileGroupRepository.findOne(id_fileGroup);
			fg.getFiles().add(new com.fullteaching.backend.file.File(1, file.getOriginalFilename(), "http://"+ this.bucketAWS +".s3.amazonaws.com/files/" + fileName));
			fg.updateFileIndexOrder();
			this.deleteStoredFile(uploadedFile);
			//ONLY ON PRODUCTION*/
			
			System.out.println("FILE SUCCESFULLY UPLOADED TO " + uploadedFile.getPath());
		}
		
		fileGroupRepository.save(fg);
		System.out.println("FINISHING METHOD!");
		return new ResponseEntity<>(this.getRootFileGroup(fg), HttpStatus.CREATED);
	}

	
	@RequestMapping("/course/{courseId}/download/{fileName:.+}")
	public void handleFileDownload(
			@PathVariable String fileName, 
			@PathVariable(value="courseId") String courseId, 
			HttpServletResponse response)
		throws FileNotFoundException, IOException {
		
		long id_course = -1;
		try {
			id_course = Long.parseLong(courseId);
		} catch(NumberFormatException e){
			return;
		}
		
		Course c = courseRepository.findOne(id_course);
		
		checkAuthorizationUsers(c, c.getAttenders());
		
		
		//ONLY ON DEVELOPMENT
		Path file = FILES_FOLDER.resolve(fileName);

		if (Files.exists(file)) {
			try {
				String fileExt = this.getFileExtension(fileName);
				response.setContentType(MimeTypes.getMimeType(fileExt));
						
				// get your file as InputStream
				InputStream is = new FileInputStream(file.toString());
				// copy it to response's OutputStream
				IOUtils.copy(is, response.getOutputStream());
				response.flushBuffer();
			} catch (IOException ex) {
				throw new RuntimeException("IOError writing file to output stream");
			}
			
		} else {
			response.sendError(404, "File" + fileName + "(" + file.toAbsolutePath() + ") does not exist");
		}
		//ONLY ON DEVELOPMENT
		
		
		/*//ONLY ON PRODUCTION
		this.productionFileDownloader(fileName, response);
		//ONLY ON PRODUCTION*/
		
	}
	
	
	@RequestMapping(value = "/upload/picture/{userId}", method = RequestMethod.POST)
	public ResponseEntity<String> handlePictureUpload(
			MultipartHttpServletRequest request,
			@PathVariable(value="userId") String userId
		) throws IOException {
		
		long id_user = -1;
		try {
			id_user = Long.parseLong(userId);
		} catch(NumberFormatException e){
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		User u = userRepository.findOne(id_user);
		
		if (!u.equals(this.user.getLoggedUser())){
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		
		Iterator<String> i = request.getFileNames();
		while (i.hasNext()) {
			String name = i.next();
			System.out.println("X - " + name);
			MultipartFile file = request.getFile(name);
			System.out.println("PICTURE: " + file.getOriginalFilename());
			
			if (file.isEmpty()) {
				System.out.println("EXCEPTION!");	
				throw new RuntimeException("The picture is empty");
			}
	
			if (!Files.exists(PICTURES_FOLDER)) {			
				System.out.println("PATH CREATED FOR PICTURE");
				Files.createDirectories(PICTURES_FOLDER);
			}
			
			String encodedName = getEncodedPictureName(file.getOriginalFilename());

			File uploadedPicture = new File(PICTURES_FOLDER.toFile(), encodedName);
			file.transferTo(uploadedPicture);
			
			//ONLY ON DEVELOPMENT
			u.setPicture(this.developingImageSaver(file));
			//ONLY ON DEVELOPMENT
			
			/*//ONLY ON PRODUCTION
			try {
				this.productionFileSaver(encodedName, "pictures", uploadedPicture);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			u.setPicture("http://elasticbeanstalk-eu-west-1-511115514439.s3.amazonaws.com/pictures/" + encodedName);
			//this.deleteStoredFile(uploadedPicture);
			//ONLY ON PRODUCTION*/
			
			userRepository.save(u);
			
			System.out.println("PICTURE SUCCESFULLY UPLOADED  TO " + uploadedPicture.getPath());
		}
		
		return new ResponseEntity<>(u.getPicture(), HttpStatus.CREATED);
	}
	
	
	
	
	//Method to get the root FileGroup of a FileGroup tree structure, given a FileGroup
	private FileGroup getRootFileGroup(FileGroup fg) {
		while(fg.getFileGroupParent() != null){
			fg = fg.getFileGroupParent();
		}
		return fg;
	}
	
	private String getFileExtension(String name){
		String[] aux = name.split("\\.");
		return aux[aux.length - 1];
	}
	
	private String getEncodedPictureName(String originalFileName){
		//Getting the image extension
		String picExtension = this.getFileExtension(originalFileName);
		//Appending a random integer to the name
		originalFileName += (Math.random() * (Integer.MIN_VALUE - Integer.MAX_VALUE));
		//Encoding original file name + random integer
		originalFileName = new BCryptPasswordEncoder().encode(originalFileName);
		//Deleting all non alphanumeric characters
		originalFileName = originalFileName.replaceAll("[^A-Za-z0-9]", "");
		//Adding the extension
		originalFileName += "." + picExtension;
		return originalFileName;
	}
	
	//Authorization checking for uploading new files (the user must be an attender)
	private ResponseEntity<Object> checkAuthorizationUsers(Object o, Collection<User> users){
		if(o == null){
			//The object does not exist
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		if(!users.contains(this.user.getLoggedUser())){
			//The user is not authorized to edit if it is not an attender of the Course
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED); 
		}
		return null;
	}
	
	//Authorization checking for editing and deleting courses (the teacher must own the Course)
	private ResponseEntity<Object> checkAuthorization(Object o, User u){
		if(o == null){
			//The object does not exist
			return new ResponseEntity<>(HttpStatus.NOT_MODIFIED);
		}
		if(!this.user.getLoggedUser().equals(u)){
			//The teacher is not authorized to edit it if he is not its owner
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED); 
		}
		return null;
	}
	
	//ONLY ON DEVELOPMENT
	private String developingImageSaver(MultipartFile file) throws IllegalStateException, IOException{
		Path DEV_PIC_FOLDER = Paths.get(System.getProperty("user.dir"), "src/main/resources/static/assets/pictures");
		if (!Files.exists(DEV_PIC_FOLDER)) {			
			Files.createDirectories(DEV_PIC_FOLDER);
		}
		String encodedName = getEncodedPictureName(file.getOriginalFilename());
		File uploadedPicture = new File(DEV_PIC_FOLDER.toFile(), encodedName);
		file.transferTo(uploadedPicture);
		return "/assets/pictures/" + encodedName;
	}
	//ONLY ON DEVELOPMENT
	
	/*//ONLY ON PRODUCTION
	private void productionFileSaver(String keyName, String folderName, File f) throws InterruptedException {
		String bucketName = this.bucketAWS + "/" + folderName;
		TransferManager tm = new TransferManager(this.amazonS3);        
        System.out.println("Hello");
        // TransferManager processes all transfers asynchronously, 
        // so this call will return immediately
        Upload upload = tm.upload(bucketName, keyName, f);
        System.out.println("Hello2");

        try {
        	// Or you can block and wait for the upload to finish
        	upload.waitForCompletion();
        	System.out.println("Upload complete.");
        } catch (AmazonClientException amazonClientException) {
        	System.out.println("Unable to upload file, upload was aborted.");
        	amazonClientException.printStackTrace();
        }
    }
	
	private void productionFileDownloader(String fileName, HttpServletResponse response) {
		String bucketName = this.bucketAWS + "/files";
        try {
            System.out.println("Downloading an object");
            S3Object s3object = this.amazonS3.getObject(new GetObjectRequest(bucketName, fileName));
            System.out.println("Content-Type: "  + s3object.getObjectMetadata().getContentType());
            
            if (s3object != null) {
    			try {
    				String fileExt = this.getFileExtension(fileName);
    				response.setContentType(MimeTypes.getMimeType(fileExt));
    				InputStream objectData = s3object.getObjectContent();
    				IOUtils.copy(objectData, response.getOutputStream());
    				response.flushBuffer();
    				objectData.close();
    			} catch (IOException ex) {
    				throw new RuntimeException("IOError writing file to output stream");
    			}
    		}
            
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which" +
            		" means your request made it " +
                    "to Amazon S3, but was rejected with an error response" +
                    " for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means"+
            		" the client encountered " +
                    "an internal error while trying to " +
                    "communicate with S3, " +
                    "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    }
	
	private void deleteStoredFile(File file){
		//Deleting stored file...
		try {
			Path path = Paths.get(FILES_FOLDER.toString(), file.getName());
		    Files.delete(path);
		} catch (NoSuchFileException x) {
		    System.err.format("%s: no such" + " file or directory%n", Paths.get(FILES_FOLDER.toString(), file.getName()));
		} catch (DirectoryNotEmptyException x) {
		    System.err.format("%s not empty%n", Paths.get(FILES_FOLDER.toString(), file.getName()));
		} catch (IOException x) {
		    // File permission problems are caught here.
		    System.err.println(x);
		}
	}
	//ONLY ON PRODUCTION*/

}