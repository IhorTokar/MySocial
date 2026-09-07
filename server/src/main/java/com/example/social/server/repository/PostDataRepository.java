package com.example.social.server.repository;

import com.example.social.server.entity.PostData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostDataRepository extends JpaRepository<PostData, Long> {
}