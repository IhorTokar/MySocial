package com.example.social.server.repository;

import com.example.social.server.entity.Followers;
import com.example.social.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FollowersRepository extends JpaRepository<Followers, Long> {
    List<Followers> findByFollower(User follower);
    List<Followers> findByFollowing(User following);
    Optional<Followers> findByFollowerAndFollowing(User follower, User following);
    long countByFollowing(User following);
}