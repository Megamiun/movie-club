resource "aws_ecr_repository" "backend" {
  name                 = "movie-club-backend"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "movie-club-backend"
  }
}

# Keeps the repository from growing unbounded -- only the 10 most recent images are kept, since the deployed EC2
# instance only ever needs whatever tag it last pulled plus a couple of previous ones for a quick manual rollback.
resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last 10 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = {
          type = "expire"
        }
      },
    ]
  })
}
