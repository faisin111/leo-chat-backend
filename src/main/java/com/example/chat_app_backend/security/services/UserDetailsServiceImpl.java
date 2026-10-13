package com.example.chat_app_backend.security.services;

import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.repository.UserRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
  private final UserRepository userRepository;

  public UserDetailsServiceImpl(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(
                () -> new UsernameNotFoundException("User Not Found with username: " + username));
    if (!"ACTIVE".equals(user.getStatus()))
      throw new DisabledException("User account is not active");
    return UserDetailsImpl.build(user);
  }
}
