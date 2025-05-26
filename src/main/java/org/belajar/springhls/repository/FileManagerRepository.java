package org.belajar.springhls.repository;

import org.belajar.springhls.model.FileManager;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FileManagerRepository extends MongoRepository<FileManager,String > {
}
