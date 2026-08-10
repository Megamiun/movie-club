# A minimal dedicated VPC instead of the account's default one -- a single public subnet, no NAT gateway (nothing
# here needs outbound-only private networking: the db runs as a container on the same public EC2 instance as the
# backend, not a separate private-subnet resource -- see infra/README.md's "What's NOT here"). No NAT gateway
# means this costs nothing beyond what the default VPC would have anyway (an Internet Gateway itself is free,
# only data transfer is billed, same as before). Single AZ, matching the single EC2 instance/EBS volume this app
# actually runs -- no reason to spread a one-box app across multiple AZs.
data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "movie-club"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "movie-club"
  }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.0.0/24"
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true # the EC2 instance needs a public IP for Caddy/SSH; there's no NAT gateway to route through otherwise

  tags = {
    Name = "movie-club-public"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "movie-club-public"
  }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}
