import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { firstValueFrom, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { UpdateProfileRequest, UserProfile } from '../../../core/models/user-profile.model';
import { UserProfileService } from '../data/user-profile.service';

@Component({
  selector: 'app-edit-profile-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './edit-profile-page.component.html',
  styleUrls: ['./edit-profile-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EditProfilePageComponent {
  private readonly profileService = inject(UserProfileService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  readonly isLoading = signal(true);
  readonly isSaving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly selectedFile = signal<File | null>(null);
  readonly previewUrl = signal<string | null>(null);

  readonly profileForm: FormGroup;
  readonly profile = toSignal(
    this.profileService.getProfile().pipe(
      switchMap((profile) => {
        this.initializeForm(profile);
        this.isLoading.set(false);
        return of(profile);
      })
    )
  );

  constructor() {
    this.profileForm = this.fb.group({
      firstName: ['', [Validators.required, Validators.minLength(2)]],
      lastName: ['', [Validators.required, Validators.minLength(2)]],
      email: ['', [Validators.required, Validators.email]],
      address: ['', [Validators.required]],
      city: [''],
      zipCode: [''],
      phone: ['']
    });
  }

  private initializeForm(profile: UserProfile): void {
    this.profileForm.patchValue({
      firstName: profile.firstName || '',
      lastName: profile.lastName || '',
      email: profile.email || '',
      address: profile.address || '',
      city: profile.city || '',
      zipCode: profile.zipCode || '',
      phone: profile.phone || ''
    });

    if (profile.avatar) {
      this.previewUrl.set(profile.avatar);
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file && file.type.startsWith('image/')) {
      this.selectedFile.set(file);
      const reader = new FileReader();
      reader.onload = (loadEvent) => {
        this.previewUrl.set(loadEvent.target?.result as string);
      };
      reader.readAsDataURL(file);
    }
  }

  async saveProfile(): Promise<void> {
    if (this.profileForm.invalid) {
      this.errorMessage.set('Veuillez corriger les erreurs du formulaire.');
      return;
    }

    this.isSaving.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    try {
      const profileData: UpdateProfileRequest = this.profileForm.getRawValue();

      if (this.selectedFile()) {
        const avatarResult = await firstValueFrom(this.profileService.uploadAvatar(this.selectedFile()!));
        if (avatarResult?.url) {
          profileData.avatar = avatarResult.url;
        }
      }

      await firstValueFrom(this.profileService.updateProfile(profileData));
      this.successMessage.set('Profil mis a jour avec succes.');
      setTimeout(() => this.router.navigate(['/dashboard']), 2000);
    } catch (error) {
      this.errorMessage.set(extractApiErrorMessage(error, 'Erreur lors de la mise a jour du profil.'));
    } finally {
      this.isSaving.set(false);
    }
  }

  cancel(): void {
    this.router.navigate(['/dashboard']);
  }
}
