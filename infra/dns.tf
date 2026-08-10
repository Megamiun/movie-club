# app.<domain> -- alias straight to CloudFront (no separate IP to manage, follows CloudFront's own DNS).
resource "aws_route53_record" "frontend" {
  zone_id = data.aws_route53_zone.this.zone_id
  name    = local.frontend_domain
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.frontend.domain_name
    zone_id                = aws_cloudfront_distribution.frontend.hosted_zone_id
    evaluate_target_health = false
  }
}

# api.<domain> -- straight to the EC2 instance's Elastic IP; Caddy on the box terminates TLS itself (see
# templates/user_data.sh.tpl), CloudFront isn't in front of the backend at all.
resource "aws_route53_record" "api" {
  zone_id = data.aws_route53_zone.this.zone_id
  name    = local.api_domain
  type    = "A"
  ttl     = 300
  records = [aws_eip.app.public_ip]
}
