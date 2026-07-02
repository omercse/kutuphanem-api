package com.booksystem.service;

import com.booksystem.dto.auth.AuthenticationRequest;
import com.booksystem.dto.auth.AuthenticationResponse;
import com.booksystem.dto.auth.RegisterRequest;

public interface IAuthenticationService {

    AuthenticationResponse register(RegisterRequest request);

    AuthenticationResponse authenticate(AuthenticationRequest request);

}