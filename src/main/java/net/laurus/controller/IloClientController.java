package net.laurus.controller;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import net.laurus.component.IloClientService;
import net.laurus.component.IloMappingService;
import net.laurus.ilo.AuthenticatedIloClient;
import net.laurus.ilo.UnauthenticatedIloClient;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IloClientController {

	@Autowired
	@Lazy
	private IloMappingService iloClientMappingService;

	@Autowired
	@Lazy
	private IloClientService iloClientService;

	@GetMapping("/unauthenticated-client-list")
	public Collection<UnauthenticatedIloClient> getUnauthenticatedClients() {
		return iloClientService.getUnauthenticatedClientData();
	}

	@GetMapping("/authenticated-client-list")
	public Collection<AuthenticatedIloClient> getAuthenticatedClients() {
		return iloClientService.getAuthenticatedClientData();
	}

	@GetMapping("/unauthenticated/{clientName}")
	public ResponseEntity<UnauthenticatedIloClient> getUnauthenticatedClientByName(@PathVariable("clientName") String clientName) {
		String clientKey = iloClientMappingService.getClientNameMappings().getOrDefault(clientName, "UNMAPPED");
		UnauthenticatedIloClient client = iloClientService.getUnauthenticatedClientData(clientKey);
		if (client != null && !clientKey.equals("UNMAPPED")) {
			return new ResponseEntity<>(client, HttpStatus.OK);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@GetMapping("/authenticated/{clientName}")
	public ResponseEntity<AuthenticatedIloClient> getAutheticatedClientByName(@PathVariable("clientName") String clientName) {
		String clientKey = iloClientMappingService.getClientNameMappings().getOrDefault(clientName, "UNMAPPED");
		AuthenticatedIloClient client = iloClientService.getAuthenticatedClientData(clientKey);
		if (client != null && !clientKey.equals("UNMAPPED")) {
			return new ResponseEntity<>(client, HttpStatus.OK);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

}
