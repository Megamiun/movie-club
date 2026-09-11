${api_domain} {
	reverse_proxy localhost:8080
}

${metrics_domain} {
	reverse_proxy localhost:3000
}
