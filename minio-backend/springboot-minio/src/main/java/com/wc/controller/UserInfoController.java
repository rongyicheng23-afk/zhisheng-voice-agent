package com.wc.controller;


import com.wc.config.MinioInfo;
import com.wc.result.result.R;
import com.wc.entity.UserInfo;
import com.wc.service.UserContractService;
import com.wc.service.UserImageService;
import com.wc.service.UserInfoService;
import io.minio.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.util.List;

@RestController
public class UserInfoController {

    @Resource
    private UserInfoService userInfoService;
    @Resource
    private MinioClient minioClient;
    @Resource
    private UserImageService userImageService;
    @Resource
    private UserContractService userContractService;

    @Resource
    private MinioInfo minioInfo;

    @GetMapping(value = "/api/users")
    public R users(){
        UserInfo current = userInfoService.getUserById(com.wc.utils.AuthContextUtil.currentUserId());
        List<UserInfo> userInfoList = current == null ? List.of() : List.of(current);
        return R.OK(userInfoList);
    }

    @PostMapping(value = "/api/user/image")
    public R image(MultipartFile file,@RequestParam(value = "id")Integer id) throws Exception {
        requireOwner(id);
        String suffix = file.getOriginalFilename().substring(file.getOriginalFilename().indexOf("."));//取文件后缀
        String object = "/api/user/image/"+id+suffix;
        ObjectWriteResponse objectWriteResponse = minioClient.putObject(PutObjectArgs.builder()
                .bucket(minioInfo.getBucket())
                .object(object)//文件的后缀不确定
                .stream(file.getInputStream(), file.getSize(), -1)//上传的大小应该不超过？
                .build()
        );
        System.out.println(objectWriteResponse);
        userImageService.saveOrUpdateUserImage(id,minioInfo.getBucket(),object);


        return R.OK();
    }

    @PostMapping(value = "/api/user/contract")
    public R contract(MultipartFile file,@RequestParam(value = "id")Integer id) throws Exception {
        requireOwner(id);
        String suffix = file.getOriginalFilename().substring(file.getOriginalFilename().indexOf("."));//取文件后缀
        String object = "/api/user/contract/"+id+suffix;
        ObjectWriteResponse objectWriteResponse = minioClient.putObject(PutObjectArgs.builder()
                .bucket(minioInfo.getBucket())
                .object(object)
                .stream(file.getInputStream(), file.getSize(), -1)//上传的大小应该不超过？
                .build()
        );
        System.out.println(objectWriteResponse);
        userContractService.saveOrUpdateUserContract(id,minioInfo.getBucket(),object);
        return R.OK();
    }

    @GetMapping(value = "/api/user/{id}")
    public R user(@PathVariable(value = "id") Integer id){
        requireOwner(id);
        UserInfo userInfo = userInfoService.getUserById(id);
        return R.OK(userInfo);
    }

    @PutMapping(value = "/api/user")
    public R updateUser(UserInfo userInfo){
        requireOwner(userInfo.getId());
        UserInfo permitted = new UserInfo();
        permitted.setId(userInfo.getId());
        permitted.setNick(userInfo.getNick());
        permitted.setSex(userInfo.getSex());
        permitted.setPhone(userInfo.getPhone());
        permitted.setEmail(userInfo.getEmail());
        permitted.setAddress(userInfo.getAddress());
        permitted.setUpdateTime(new java.util.Date());
        boolean update = userInfoService.updateById(permitted);
        return update ? R.OK():R.FAIL();
    }

    @GetMapping(value = "/api/download/{id}")
    public void download(@PathVariable(value = "id")Integer id, HttpServletResponse response) throws Exception {
        requireOwner(id);

        UserInfo user = userInfoService.getUserById(id);

        String bucket = user.getUserContractDO().getBucket();
        String fileName = user.getUserContractDO().getObject();
        //要让浏览器弹出下载框，后端需要设置一下响应头信息
        response.setContentType("application/octet-stream");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-disposition","attachment;filename="+ URLEncoder.encode(fileName, StandardCharsets.UTF_8));

        try (GetObjectResponse object = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(fileName)
                .build())) {
        object.transferTo(response.getOutputStream());//之前是获得输出流下载到本地磁盘，这里是response中
        }
    }
    @DeleteMapping(value = "/api/user/{id}")
    public R deleteUser(@PathVariable(value = "id")Integer id) throws Exception {
        requireOwner(id);
        return userInfoService.deleteUserById(id) ? R.OK():R.FAIL();
    }

    private void requireOwner(Integer id) {
        if (id == null || !id.equals(com.wc.utils.AuthContextUtil.currentUserId())
                || userInfoService.getUserById(id) == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "无权访问该用户资源");
        }
    }
}
