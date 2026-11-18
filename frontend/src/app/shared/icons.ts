import { EnvironmentProviders, importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import {
  AlertTriangle,
  Bell,
  Building2,
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock,
  Coins,
  Download,
  Eye,
  FileCheck2,
  FileStack,
  Files,
  FileText,
  HardDrive,
  KeyRound,
  Layers,
  LayoutDashboard,
  LayoutTemplate,
  ListChecks,
  LogOut,
  MessageSquare,
  Pencil,
  Plus,
  RefreshCw,
  Save,
  Search,
  Settings,
  ShieldCheck,
  Sparkles,
  Trash2,
  UploadCloud,
  User,
  Users,
  X,
  ZoomIn,
  ZoomOut
} from 'lucide-angular';

const ICONS = {
  AlertTriangle,
  Bell,
  Building2,
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock,
  Coins,
  Download,
  Eye,
  FileCheck2,
  FileStack,
  Files,
  FileText,
  HardDrive,
  KeyRound,
  Layers,
  LayoutDashboard,
  LayoutTemplate,
  ListChecks,
  LogOut,
  MessageSquare,
  Pencil,
  Plus,
  RefreshCw,
  Save,
  Search,
  Settings,
  ShieldCheck,
  Sparkles,
  Trash2,
  UploadCloud,
  User,
  Users,
  X,
  ZoomIn,
  ZoomOut
};

/**
 * Icon NgModule shared by every standalone component.
 * The actual icon set is registered once at bootstrap via {@link provideLucideIcons}.
 */
export const IconModule = LucideAngularModule;

/**
 * Registers the DocuMind icon set for the whole application.
 */
export function provideLucideIcons(): EnvironmentProviders {
  return importProvidersFrom(LucideAngularModule.pick(ICONS));
}
