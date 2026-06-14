package pl.zydron.platform.platformcore.tenants;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class OrganizationMemberId implements Serializable {

    private UUID organizationId;

    private UUID userId;
}
