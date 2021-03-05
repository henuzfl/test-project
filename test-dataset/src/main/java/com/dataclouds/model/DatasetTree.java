package com.dataclouds.model;

import com.alibaba.fastjson.JSONObject;
import com.dataclouds.adapter.output.dfs.IFileSystemService;
import com.dataclouds.exceptions.NameExistsException;
import com.dataclouds.exceptions.PathNotExistsException;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @Author: zfl
 * @Date: 2021/3/5 15:32
 * @Version: 1.0.0
 */
public class DatasetTree {


    private IFileSystemService fileSystemService;
    private DatasetDir root;

    public DatasetTree(IFileSystemService fileSystemService,
                       JSONObject treeJson) {
        this.fileSystemService = fileSystemService;
        if (!treeJson.isEmpty()) {
            this.root = decode(treeJson).get();
        } else {
            DatasetDir root = new DatasetDir();
            root.setName("root");
            root.setChildrenFiles(new ArrayList<DatasetFile>());
            root.setChildrenDirs(new ArrayList<DatasetDir>());
            this.root = root;
        }
    }

    public void addDir(String path, String name) {
        DatasetDir parent = findDir(path);
        parent.getChildrenDirs().stream()
                .filter(d -> d.getName().equals(name))
                .findAny()
                .ifPresent(d -> {
                    throw new NameExistsException(path, name);
                });
        DatasetDir dir = new DatasetDir();
        dir.setName(name);
        dir.setChildrenDirs(new ArrayList<DatasetDir>());
        dir.setChildrenFiles(new ArrayList<DatasetFile>());
        parent.getChildrenDirs().add(dir);
    }

    public void addFile(String path, String name) {
        DatasetDir parent = findDir(path);
        parent.getChildrenFiles().stream()
                .filter(d -> d.getName().equals(name))
                .findAny()
                .ifPresent(d -> {
                    throw new NameExistsException(path, name);
                });
        DatasetFile file = new DatasetFile();
        file.setName(name);
        parent.getChildrenFiles().add(file);
    }

    public void upload(String path, InputStream inputStream) {
        DatasetFile datasetFile = findFile(path);
        String dfsPath = fileSystemService.upload(datasetFile.getName(),
                inputStream);
        datasetFile.setDfsPath(dfsPath);
    }

    public List<JSONObject> list(String path) {
        DatasetDir parent = findDir(path);
        List<JSONObject> result = new ArrayList<>();
        result.addAll(parent.getChildrenDirs().stream()
                .map(dir -> {
                    JSONObject tmp = new JSONObject();
                    tmp.put("name", dir.getName());
                    tmp.put("type", "dir");
                    return tmp;
                })
                .collect(Collectors.toList()));
        result.addAll(parent.getChildrenFiles().stream()
                .map(file -> {
                    JSONObject tmp = new JSONObject();
                    tmp.put("name", file.getName());
                    tmp.put("type", "file");
                    if (null != file.getDfsPath()) {
                        tmp.put("url",
                                fileSystemService.getUrl(file.getDfsPath()));
                    }
                    return tmp;
                })
                .collect(Collectors.toList()));
        return result;
    }

    private DatasetDir findDir(String path) {
        DatasetDir result = root;
        for (String p : path.split("/")) {
            if (!StringUtils.isEmpty(p.trim())) {
                result = result.getChildrenDirs().stream()
                        .filter(d -> d.getName().equals(p.trim()))
                        .findAny()
                        .orElseThrow(() -> new PathNotExistsException(path));
            }
        }
        return result;
    }

    public JSONObject encode() {
        return encodeDir(this.root);
    }

    public Optional<DatasetDir> decode(JSONObject treeJson) {
        if (treeJson.containsKey("type") && StringUtils.equals(treeJson.getString("type"), "dir")) {
            DatasetDir dir = new DatasetDir();
            dir.setName(treeJson.getString("name"));
            if (treeJson.containsKey("childrenDirs")) {
                dir.setChildrenDirs(treeJson.getJSONArray("childrenDirs")
                        .toJavaList(JSONObject.class).stream()
                        .map(this::decode).filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList()));
            } else {
                dir.setChildrenDirs(new ArrayList<>());
            }
            if (treeJson.containsKey("childrenFiles")) {
                dir.setChildrenFiles(treeJson.getJSONArray("childrenFiles")
                        .toJavaList(JSONObject.class).stream()
                        .map(tmpJson -> {
                            DatasetFile file = new DatasetFile();
                            file.setName(tmpJson.getString("name"));
                            if (tmpJson.containsKey("dfsPath")) {
                                file.setDfsPath(tmpJson.getString("dfsPath"));
                            }
                            return file;
                        }).collect(Collectors.toList()));
            } else {
                dir.setChildrenDirs(new ArrayList<>());
            }
            return Optional.of(dir);
        }
        return Optional.empty();
    }

    private JSONObject encodeDir(DatasetDir dir) {
        JSONObject result = new JSONObject();
        result.put("name", dir.getName());
        result.put("type", "dir");
        result.put("childrenFiles", dir.getChildrenFiles().stream()
                .map(file -> {
                    JSONObject tmp = new JSONObject();
                    tmp.put("name", file.getName());
                    tmp.put("type", "file");
                    if (null != file.getDfsPath()) {
                        tmp.put("dfsPath", file.getDfsPath());
                    }
                    return tmp;
                })
                .collect(Collectors.toList()));
        result.put("childrenDirs", dir.getChildrenDirs().stream()
                .map(this::encodeDir)
                .collect(Collectors.toList()));
        return result;
    }


    private DatasetFile findFile(String path) {
        DatasetDir parent = root;
        String[] filePathArr = path.split("/");
        String fileName = filePathArr[filePathArr.length - 1];
        for (int i = 0; i < filePathArr.length - 1; i++) {
            String dirName = filePathArr[i];
            if (StringUtils.isNotBlank(dirName)) {
                parent = parent.getChildrenDirs().stream()
                        .filter(d -> d.getName().equals(dirName))
                        .findAny()
                        .orElseThrow(() -> new PathNotExistsException(path));
            }
        }
        return parent.getChildrenFiles().stream()
                .filter(f -> f.getName().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new PathNotExistsException(path));
    }

}