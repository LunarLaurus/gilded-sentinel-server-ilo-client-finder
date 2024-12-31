package net.laurus.component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.extern.java.Log;
import net.laurus.data.dto.ipmi.ilo.IloRegistrationRequest;
import net.laurus.ilo.AuthenticatedIloClient;
import net.laurus.ilo.UnauthenticatedEndpoint;
import net.laurus.ilo.UnauthenticatedIloClient;
import net.laurus.network.IPv4Address;

@Service
@Log
public class IloMappingService {
	
	@Getter
	private final Map<IPv4Address, String> clientAddressMappings = new ConcurrentHashMap<>();
	
	private final Map<String, String> clientIloNameMappings = new ConcurrentHashMap<>();

	@Lazy
	private final IloClientService iloClientService;

	@Lazy
	private final IloAuthService iloAuthService;

	@Autowired
	public IloMappingService(IloClientService iloClientService, IloAuthService iloAuthService) {
		this.iloClientService = iloClientService;
		this.iloAuthService = iloAuthService;
	}

	public boolean isClientMapped(String clientId) {
		return clientIloNameMappings.containsKey(clientId);
	}

	public Map<String, String> getClientNameMappings() {
		return clientIloNameMappings;
	}

	public String mapClient(IloRegistrationRequest clientObject) {
		String iloKey = "NEEDS_WORK";
		if (!isClientMapped(clientObject.getHostClientId())) {
			if (iloKey != null) {
				clientAddressMappings.put(clientObject.getIloAddress(), iloKey);
				UnauthenticatedIloClient unauthClient;
				try {
					unauthClient = UnauthenticatedEndpoint.getIloClient(clientObject.getIloAddress());
					iloClientService.addUnauthenticatedClient(iloKey, unauthClient);
					AuthenticatedIloClient authData = AuthenticatedIloClient.from(iloAuthService.getDefaultIloUser(), clientObject.getIloAddress(), unauthClient);
					if (authData != null) {
						iloClientService.addAuthenticatedClient(iloKey, authData);
						clientIloNameMappings.put(clientObject.getHostClientId(), iloKey);					
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				return iloKey;
			}
		} else {
			log.info("Tried to register " + clientObject + ", " + iloKey + ", however it is already mapped. Ignoring.");
			return iloKey;
		}
		return "UNMAPPED";
	}
}
