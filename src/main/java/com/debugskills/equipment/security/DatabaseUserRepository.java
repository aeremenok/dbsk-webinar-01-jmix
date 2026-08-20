package com.debugskills.equipment.security;

import com.debugskills.equipment.entity.User;
import com.debugskills.equipment.entity.UserRole;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.securitydata.user.AbstractDatabaseUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Primary
@Component("equipment_UserRepository")
public class DatabaseUserRepository extends AbstractDatabaseUserRepository<User> {

    @Autowired
    private UnconstrainedDataManager dataManager;

    @Override
    protected Class<User> getUserClass() {
        return User.class;
    }

    @Override
    protected void initSystemUser(User systemUser) {
        Collection<GrantedAuthority> authorities = getGrantedAuthoritiesBuilder()
                .addResourceRole(FullAccessRole.CODE)
                .build();
        systemUser.setAuthorities(authorities);
    }

    @Override
    protected void initAnonymousUser(User anonymousUser) {
        // no anonymous authorities in this starter
    }

    /**
     * Loads a user and attaches the resource roles assigned to it in the
     * {@code EQUIP_USER_ROLE} table as granted authorities.
     */
    @Override
    public User loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = super.loadUserByUsername(username);

        List<UserRole> userRoles = dataManager.load(UserRole.class)
                .query("select ur from equipment_UserRole ur where ur.user.id = :userId")
                .parameter("userId", user.getId())
                .fetchPlan("_base")
                .list();

        var authoritiesBuilder = getGrantedAuthoritiesBuilder();
        for (UserRole userRole : userRoles) {
            authoritiesBuilder.addResourceRole(userRole.getRoleCode());
        }
        user.setAuthorities(authoritiesBuilder.build());

        return user;
    }
}
