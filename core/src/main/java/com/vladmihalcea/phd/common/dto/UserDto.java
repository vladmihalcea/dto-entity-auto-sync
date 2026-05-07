package com.vladmihalcea.phd.common.dto;

import java.util.Objects;

/**
 * @author Vlad Mihalcea
 */
public class UserDto {

    private Long id;

    private String email;

    private String firstName;

    private String lastName;

    public Long getId() {
        return id;
    }

    public UserDto setId(Long id) {
        this.id = id;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public UserDto setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getFirstName() {
        return firstName;
    }

    public UserDto setFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public String getLastName() {
        return lastName;
    }

    public UserDto setLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UserDto userDTO)) return false;
        return Objects.equals(getId(), userDTO.getId()) && Objects.equals(getEmail(), userDTO.getEmail()) && Objects.equals(getFirstName(), userDTO.getFirstName()) && Objects.equals(getLastName(), userDTO.getLastName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getEmail(), getFirstName(), getLastName());
    }
}
