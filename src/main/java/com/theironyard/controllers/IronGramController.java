package com.theironyard.controllers;

import com.sun.deploy.net.HttpResponse;
import com.theironyard.entities.Photo;
import com.theironyard.entities.User;
import com.theironyard.services.PhotoRepository;
import com.theironyard.services.UserRepository;
import com.theironyard.utils.PasswordHash;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by landonkail on 11/17/15.
 */
@RestController
public class IronGramController {
    @Autowired
    UserRepository users;

    @Autowired
    PhotoRepository photos;

    @RequestMapping("/login")
    public User login(HttpSession session, HttpServletResponse response, String username, String password) throws Exception {
        User user = users.findOneByUsername(username);

        if (user == null) {
            user = new User();
            user.username = username;
            user.password = PasswordHash.createHash(password);
            users.save(user);
        } else if (!PasswordHash.validatePassword(password, user.password)) {
            throw new Exception("Wrong Password");
        }

        session.setAttribute("username", username);
        response.sendRedirect("/");

        return user;
    }

    @RequestMapping("/logout")
    public void logout(HttpSession session, HttpServletResponse response) throws IOException {
        session.invalidate();
        response.sendRedirect("/");
    }

    @RequestMapping("/user")
    public User user(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return null;
        }
        return users.findOneByUsername(username);
    }

    @RequestMapping("/upload")
    public Photo upload(HttpSession session, HttpServletResponse response, String receiver, MultipartFile photo, int userTime, boolean isPublic) throws Exception {
        String username = (String) session.getAttribute("username");


        if (username == null) {
            throw new Exception("Not logged in");
        }

        User senderUser = users.findOneByUsername(username);
        User receiverUser =  users.findOneByUsername(receiver);

        if (receiverUser == null) {
            throw new Exception("Receiver name doesn't exist.");
        }

        File photoFile = File.createTempFile("photo", photo.getOriginalFilename(), new File("public"));
        FileOutputStream fos = new FileOutputStream(photoFile);
        fos.write(photo.getBytes());

        Photo p = new Photo();
        p.sender = senderUser;
        p.receiver = receiverUser;
        p.filename = photoFile.getName();
        p.userTime = userTime;
        p.isPublic = isPublic;
        photos.save(p);

        response.sendRedirect("/");

        return p;
    }

    @RequestMapping("/public-photos")
    public List<Photo> publicPhotos(String username) {
        User user = users.findOneByUsername(username);

        List<Photo> selectedPhotos = photos.findBySender(user).stream()
                .filter(p1 -> p1.isPublic)
                .collect(Collectors.toList());
        return selectedPhotos;
    }

    @RequestMapping("/photos")
    public List<Photo> showPhotos(HttpSession session) throws Exception {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            throw new Exception("Not logged in.");
        }

        User user = users.findOneByUsername(username);
        List<Photo> photosList = photos.findByReceiver(user);

        List<Photo> selectedPhotos = photosList.stream()
                .filter(p1 -> {
                    if (p1.deleteTime == null) {
                        p1.deleteTime = LocalDateTime.now().plusSeconds(p1.userTime);
                        photos.save(p1);
                    } else if (p1.deleteTime.isBefore(LocalDateTime.now())) {
                        photos.delete(p1);
                        File f = new File("public/" + p1.filename);
                        f.delete();
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        return selectedPhotos;
    }

        /*
        for (Photo p : photosList) {
            if (p.deleteTime == null) {
                p.deleteTime = LocalDateTime.now().plusSeconds(p.userTime);
                photos.save(p);
            } else if (p.deleteTime.isBefore(LocalDateTime.now())) {
                photos.delete(p);
                File f = new File("public/" + p.filename);
                f.delete();
            }
        }

        return photos.findByReceiver(user);
    }
    */


}
